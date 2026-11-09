package uz.skillswap.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.skillswap.app.data.remote.ApiClient
import uz.skillswap.app.data.remote.ApiException
import uz.skillswap.app.data.remote.AuthResult
import uz.skillswap.app.data.remote.OfflineException
import uz.skillswap.app.data.remote.Snapshot
import uz.skillswap.app.util.Security
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
 * Two kinds of account:
 *  - ONLINE account (has a server token): every change goes to the server first, then the
 *    phone downloads a fresh copy into Room. Same account works on the website.
 *  - PHONE account (no token, e.g. offline demo): everything stays in Room on this phone.
 * The screens always read from Room, so they work the same way in both cases, even without internet.
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

    private val token: String? get() = prefs.getString(KEY_TOKEN, null)

    // ---------- start-up ----------
    suspend fun seedIfEmpty() {
        if (db.skillDao().count() > 0) return
        val skillIds = SampleData.skills.associate { it.name to db.skillDao().insert(it) }
        val userIds = SampleData.users.associate { it.email to db.userDao().insert(it) }
        val offerIds = HashMap<String, Long>()
        for (o in SampleData.offers) {
            offerIds["${o.teacherEmail}|${o.skill}"] = db.offerDao().createOffer(
                userIds.getValue(o.teacherEmail), skillIds.getValue(o.skill), o.level, o.description, o.slots
            )
        }
        for (l in SampleData.lessons) {
            val offerId = offerIds.getValue("${l.teacherEmail}|${l.skill}")
            val slot = db.offerDao().slotsOf(offerId)[l.slotIndex]
            val learnerId = userIds.getValue(l.learnerEmail)
            val bookingId = db.bookingDao().book(learnerId, slot.id)
            db.bookingDao().complete(learnerId, bookingId)
            db.bookingDao().addReview(learnerId, bookingId, l.rating, l.comment)
        }
    }

    suspend fun restoreSession() {
        val id = prefs.getLong(KEY_USER_ID, -1)
        if (id > 0) _currentUser.value = db.userDao().findById(id)
        if (token != null) _sync.value = SyncState(online = true, lastSync = prefs.getLong(KEY_LAST_SYNC, 0L))
    }

    /** Download a fresh copy from the server. Safe to call any time; problems end up in [sync]. */
    suspend fun refresh() {
        val t = token ?: return
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

    private suspend fun applySnapshot(s: Snapshot) {
        db.syncDao().replaceAll(s.users, s.skills, s.offers, s.slots, s.bookings, s.reviews)
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
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
            registerOnPhone(fullName.trim(), mail, password)     // no internet: keep working on the phone
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        val mail = email.trim().lowercase()
        return try {
            Result.success(startOnlineSession(api.login(mail, password)))
        } catch (e: ApiException) {
            // 401 = not a server account; it may be an account that exists only on this phone
            if (e.status == 401) loginOnPhone(mail, password) ?: Result.failure(e) else Result.failure(e)
        } catch (e: OfflineException) {
            loginOnPhone(mail, password)
                ?: Result.failure(IllegalStateException("No internet, and this account is not saved on this phone"))
        }
    }

    private suspend fun startOnlineSession(auth: AuthResult): User {
        prefs.edit().putString(KEY_TOKEN, auth.token).putLong(KEY_USER_ID, auth.user.id).apply()
        _sync.value = SyncState(online = true)
        _currentUser.value = auth.user
        refresh()                                   // download the real data into Room
        return _currentUser.value ?: auth.user
    }

    private suspend fun loginOnPhone(mail: String, password: String): Result<User>? {
        val user = db.userDao().findByEmail(mail) ?: return null
        if (user.passwordHash.isEmpty() || user.passwordHash != Security.hashPassword(password, mail)) return null
        startPhoneSession(user)
        return Result.success(user)
    }

    private suspend fun registerOnPhone(fullName: String, mail: String, password: String): Result<User> {
        if (db.userDao().findByEmail(mail) != null) {
            return Result.failure(IllegalArgumentException("This e-mail is already registered"))
        }
        val user = User(fullName = fullName, email = mail, passwordHash = Security.hashPassword(password, mail))
        val saved = user.copy(id = db.userDao().insert(user))
        startPhoneSession(saved)
        return Result.success(saved)
    }

    private fun startPhoneSession(user: User) {
        prefs.edit().remove(KEY_TOKEN).putLong(KEY_USER_ID, user.id).apply()
        _sync.value = SyncState(online = false)
        _currentUser.value = user
    }

    suspend fun updateProfile(name: String, bio: String): Result<Unit> {
        if (name.trim().length < 2) return Result.failure(IllegalArgumentException("Name is too short"))
        val id = requireUserId()
        val t = token
        if (t != null) {
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
        }
        db.userDao().updateProfile(id, name.trim(), bio.trim())
        _currentUser.value = db.userDao().findById(id)
        return Result.success(Unit)
    }

    suspend fun logout() {
        val t = token
        if (t != null) {
            try { api.logout(t) } catch (e: Exception) { /* offline: the server session simply expires */ }
            // forget the downloaded data and bring back the offline demo
            db.syncDao().clearAll()
            seedIfEmpty()
        }
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER_ID).remove(KEY_LAST_SYNC).apply()
        _sync.value = SyncState()
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

    // ---------- changes: server first when online, otherwise Room on the phone ----------
    suspend fun createOffer(skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>): Long =
        onServer { api.publishOffer(it, skillId, level, description.trim(), slots) }
            ?: db.offerDao().createOffer(requireUserId(), skillId, level, description.trim(), slots)

    suspend fun book(slotId: Long): Long =
        onServer { api.book(it, slotId) } ?: db.bookingDao().book(requireUserId(), slotId)

    suspend fun cancel(bookingId: Long) {
        onServer { api.cancel(it, bookingId) } ?: db.bookingDao().cancel(requireUserId(), bookingId)
    }

    suspend fun complete(bookingId: Long) {
        onServer { api.complete(it, bookingId) } ?: db.bookingDao().complete(requireUserId(), bookingId)
    }

    suspend fun review(bookingId: Long, rating: Int, comment: String): Long =
        onServer { api.review(it, bookingId, rating, comment) }
            ?: db.bookingDao().addReview(requireUserId(), bookingId, rating, comment)

    /**
     * Online account: run [action] on the server, then download the fresh data and return the result.
     * Phone account: returns null, so the caller does the change in Room instead.
     * Server errors (e.g. "this time was just booked") are thrown and shown on screen.
     */
    private suspend fun <T> onServer(action: suspend (String) -> T): T? {
        val t = token ?: return null
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

    private fun requireUserId(): Long =
        currentUser.value?.id ?: throw IllegalStateException("Please log in first")

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "server_token"
        const val KEY_LAST_SYNC = "last_sync"
        const val SESSION_EXPIRED = "Your session expired. Please log in again."
    }
}
