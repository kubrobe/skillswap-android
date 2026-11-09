package uz.skillswap.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.skillswap.app.data.remote.ApiClient
import uz.skillswap.app.data.remote.ApiException
import uz.skillswap.app.data.remote.AuthResult
import uz.skillswap.app.data.remote.Match
import uz.skillswap.app.data.remote.OfflineException
import uz.skillswap.app.data.remote.Snapshot
import uz.skillswap.app.data.remote.Wallet
import uz.skillswap.app.util.TokenCipher
import uz.skillswap.app.util.Validators

/** What the small status pill on the Browse screen shows. */
data class SyncState(
    val online: Boolean = false,        // true = account on the SkillSwap server, false = only on this phone
    val syncing: Boolean = false,
    val lastSync: Long = 0L,
    val problem: String? = null         // e.g. "You are offline..." -> the app shows saved data
)

/**
 * The single place that talks to data. Screens never use DAOs or the network directly.
 *
 * The app is always online: Supabase (through our server) is the ONLY source of truth.
 * Every change goes to the server first, then the phone downloads a fresh copy into Room.
 * Room is just a cache, so the screens are instant and update by themselves.
 * The same account works on the website.
 */
class SkillSwapRepository(
    private val db: AppDatabase,
    private val prefs: SharedPreferences,
    private val api: ApiClient
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    // Online-only extras from the server (not stored in Room tables)
    private val _wallet = MutableStateFlow<Wallet?>(null)
    val wallet: StateFlow<Wallet?> = _wallet.asStateFlow()
    private val _wants = MutableStateFlow<List<Long>>(emptyList())
    val wants: StateFlow<List<Long>> = _wants.asStateFlow()
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    /** Live messages from the server, e.g. "Bobur booked your Python lesson". Shown as a banner. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    // The session token is saved ENCRYPTED (see TokenCipher) and kept in memory after the first read.
    private var cachedToken: String? = null
    private var tokenLoaded = false

    private val token: String?
        get() {
            if (!tokenLoaded) {
                cachedToken = prefs.getString(KEY_TOKEN, null)?.let { TokenCipher.decrypt(it) }
                tokenLoaded = true
            }
            return cachedToken
        }

    private fun saveToken(value: String?) {
        cachedToken = value
        tokenLoaded = true
        val editor = prefs.edit()
        if (value == null) {
            editor.remove(KEY_TOKEN)
        } else {
            try {
                editor.putString(KEY_TOKEN, TokenCipher.encrypt(value))
            } catch (e: Exception) {
                editor.remove(KEY_TOKEN)        // no secure storage: the token lives only in memory
            }
        }
        editor.apply()
    }

    // ---------- start-up ----------
    suspend fun restoreSession() {
        if (token == null) return                       // not logged in
        _sync.value = SyncState(online = true, lastSync = prefs.getLong(KEY_LAST_SYNC, 0L))
        _wallet.value = Wallet(prefs.getInt(KEY_BALANCE, 0), emptyList())    // until the first sync
        val id = prefs.getLong(KEY_USER_ID, -1)
        if (id > 0) _currentUser.value = db.userDao().findById(id)
        if (_currentUser.value == null) refresh()      // empty cache (first start after an update): download now
    }

    /** Download a fresh copy from the server. Safe to call any time; problems end up in [sync]. */
    suspend fun refresh() {
        val t = token ?: return
        if (_sync.value.syncing) return                 // one download at a time
        _sync.value = _sync.value.copy(syncing = true, problem = null)
        try {
            applySnapshot(api.sync(t))
            _sync.value = SyncState(online = true, lastSync = prefs.getLong(KEY_LAST_SYNC, 0L))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.status == 401) expireSession()
            else _sync.value = _sync.value.copy(syncing = false, problem = e.message)
        } catch (e: OfflineException) {
            _sync.value = _sync.value.copy(syncing = false, problem = e.message)
        } catch (e: Exception) {
            // unexpected answer (e.g. a Wi-Fi login page instead of our server): keep the saved data
            _sync.value = _sync.value.copy(syncing = false, problem = "Could not update the data. Showing saved data.")
        }
    }

    /** Called when the user comes back to the app: download again only if the data is older than 1 minute. */
    suspend fun refreshIfStale() {
        if (token == null) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_SYNC, 0L) > 60_000L) refresh()
    }

    /** The free server sleeps; poke it at start-up so it is awake by the time the user taps "Log in". */
    suspend fun wakeServer() {
        try { api.health() } catch (e: Exception) { /* offline is fine here */ }
    }

    private suspend fun applySnapshot(s: Snapshot) {
        db.syncDao().replaceAll(s.users, s.skills, s.offers, s.slots, s.bookings, s.reviews)
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).putInt(KEY_BALANCE, s.wallet.balance).apply()
        _wallet.value = s.wallet
        _wants.value = s.wants
        _matches.value = s.matches
        _currentUser.value = db.userDao().findById(s.me.id) ?: s.me
    }

    // ---------- accounts ----------
    suspend fun register(fullName: String, email: String, password: String): Result<User> {
        val mail = email.trim().lowercase()
        if (fullName.trim().length < 2) return Result.failure(IllegalArgumentException("Enter your name"))
        if (!Validators.isValidEmail(mail)) return Result.failure(IllegalArgumentException("Invalid e-mail"))
        if (!Validators.isStrongPassword(password)) {
            return Result.failure(IllegalArgumentException("Password: min 8 characters and 1 digit"))
        }
        return try {
            Result.success(startOnlineSession(api.register(fullName.trim(), mail, password)))
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: OfflineException) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        val mail = email.trim().lowercase()
        return try {
            Result.success(startOnlineSession(api.login(mail, password)))
        } catch (e: ApiException) {
            Result.failure(e)
        } catch (e: OfflineException) {
            Result.failure(e)
        }
    }

    private suspend fun startOnlineSession(auth: AuthResult): User {
        saveToken(auth.token)
        prefs.edit().putLong(KEY_USER_ID, auth.user.id).apply()
        _sync.value = SyncState(online = true)
        _currentUser.value = auth.user
        refresh()                                   // download the real data into Room
        return _currentUser.value ?: auth.user
    }

    suspend fun updateProfile(name: String, bio: String): Result<Unit> {
        if (name.trim().length < 2) return Result.failure(IllegalArgumentException("Name is too short"))
        val id = requireUserId()
        val t = token ?: return Result.failure(IllegalStateException("Please log in first"))
        try {
            api.updateProfile(t, name.trim(), bio.trim())
        } catch (e: ApiException) {
            if (e.status == 401) {
                expireSession()
                return Result.failure(IllegalStateException(SESSION_EXPIRED))
            }
            return Result.failure(e)
        } catch (e: OfflineException) {
            return Result.failure(e)
        }
        db.userDao().updateProfile(id, name.trim(), bio.trim())
        _currentUser.value = db.userDao().findById(id)
        return Result.success(Unit)
    }

    suspend fun logout() {
        val t = token
        if (t != null) {
            try { api.logout(t) } catch (e: Exception) { /* offline: the server session simply expires */ }
        }
        db.syncDao().clearAll()                          // forget the downloaded copy
        saveToken(null)
        prefs.edit().remove(KEY_USER_ID).remove(KEY_LAST_SYNC).remove(KEY_BALANCE).apply()
        _sync.value = SyncState()
        _wallet.value = null
        _wants.value = emptyList()
        _matches.value = emptyList()
        _currentUser.value = null
    }

    /** The server no longer knows our token (expired or logged out elsewhere). */
    private suspend fun expireSession() {
        logout()
        _sync.value = SyncState(problem = SESSION_EXPIRED)
    }

    // ---------- catalogue (always read from Room) ----------
    fun offers(query: String, category: String?): Flow<List<OfferCard>> =
        db.offerDao().search(query.trim(), category)

    fun categories(): Flow<List<String>> = db.skillDao().categories()
    fun skills(): Flow<List<Skill>> = db.skillDao().observeAll()
    fun offer(id: Long): Flow<OfferCard?> = db.offerDao().observeOffer(id)
    fun slots(offerId: Long): Flow<List<TimeSlot>> = db.offerDao().observeSlots(offerId)
    fun reviews(offerId: Long): Flow<List<ReviewLine>> = db.offerDao().observeReviews(offerId)
    fun myOffers(userId: Long): Flow<List<OfferCard>> = db.offerDao().observeMine(userId)
    fun learning(userId: Long): Flow<List<BookingLine>> = db.bookingDao().observeLearning(userId)
    fun teaching(userId: Long): Flow<List<BookingLine>> = db.bookingDao().observeTeaching(userId)

    // ---------- changes: always on the server (it checks every rule in a transaction) ----------
    suspend fun createOffer(skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>): Long =
        onServer { api.publishOffer(it, skillId, level, description.trim(), slots) }

    suspend fun book(slotId: Long): Long = onServer { api.book(it, slotId) }

    suspend fun cancel(bookingId: Long) = onServer { api.cancel(it, bookingId) }

    suspend fun complete(bookingId: Long, code: String) = onServer { api.complete(it, bookingId, code.trim()) }

    suspend fun review(bookingId: Long, rating: Int, comment: String): Long =
        onServer { api.review(it, bookingId, rating, comment) }

    /**
     * Run [action] on the server, then download the fresh data and return the result.
     * Server errors (e.g. "this time was just booked") are thrown and shown on screen.
     */
    private suspend fun <T> onServer(action: suspend (String) -> T): T {
        val t = token ?: throw IllegalStateException("Please log in first")
        requireUserId()
        val result = try {
            action(t)
        } catch (e: ApiException) {
            if (e.status == 401) {
                expireSession()
                throw IllegalStateException(SESSION_EXPIRED)
            }
            refresh()                               // someone else may have changed the data
            throw e
        }
        refresh()
        return result
    }

    // ---------- hackathon features (online accounts) ----------

    /** Save "what I want to learn"; the server then finds perfect matches. */
    suspend fun setWants(skillIds: List<Long>) = onServer { api.setWants(it, skillIds) }

    /** A 60-minute plan for a booked lesson: (plan, "ai" or "template"). */
    suspend fun lessonPlan(bookingId: Long): Pair<String, String> {
        val t = token ?: throw IllegalStateException("Please log in first")
        return api.lessonPlan(t, bookingId)
    }

    /**
     * Keeps a live connection to the server while the app is on screen. When anything changes,
     * the phone downloads a fresh copy at once; personal messages go to [notices].
     * If the line breaks (no internet, server restart) it reconnects after 2, 4, 8 ... 30 seconds.
     */
    suspend fun runLiveUpdates() {
        var wait = 2_000L
        while (true) {
            val t = token ?: return
            try {
                api.listen(t) { type, data ->
                    when (type) {
                        "hello" -> {
                            wait = 2_000L
                            if (_sync.value.problem != null) refresh()     // back online: clear the problem
                        }
                        "notice" -> { _notices.tryEmit(data.optString("text")) }
                        "changed" -> refresh()
                    }
                    Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) {
                    expireSession()
                    return
                }
            } catch (e: Exception) {
                // offline or the server restarted: try again below
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(30_000L)
        }
    }

    private fun requireUserId(): Long =
        currentUser.value?.id ?: throw IllegalStateException("Please log in first")

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "server_token"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_BALANCE = "wallet_balance"
        const val SESSION_EXPIRED = "Your session expired. Please log in again."
    }
}
