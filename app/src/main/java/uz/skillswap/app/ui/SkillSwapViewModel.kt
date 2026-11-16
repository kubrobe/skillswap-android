package uz.skillswap.app.ui

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import uz.skillswap.app.data.remote.Achievements
import uz.skillswap.app.data.remote.TelegramStatus
import uz.skillswap.app.data.remote.ProfileEdit
import uz.skillswap.app.data.remote.UsernameCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import uz.skillswap.app.data.remote.MatchResult
import uz.skillswap.app.data.remote.Galaxy
import java.util.Locale
import uz.skillswap.app.ui.i18n.Lang
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.skillswap.app.SkillSwapApp
import uz.skillswap.app.data.BookingLine
import uz.skillswap.app.data.OfferCard
import uz.skillswap.app.data.ReviewLine
import uz.skillswap.app.data.Skill
import uz.skillswap.app.data.SkillSwapRepository
import uz.skillswap.app.data.SyncState
import uz.skillswap.app.data.TimeSlot
import uz.skillswap.app.data.remote.ActivityLine
import uz.skillswap.app.data.remote.Impact
import uz.skillswap.app.data.remote.Match
import uz.skillswap.app.data.remote.Wallet
import uz.skillswap.app.data.remote.RoomPresence
import uz.skillswap.app.data.User
import uz.skillswap.app.data.STATUS_DONE
import uz.skillswap.app.util.memberLevel

/** Holds screen state and survives screen rotation. Screens call its functions. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SkillSwapViewModel(private val repo: SkillSwapRepository) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()     // true after start-up work

    val currentUser: StateFlow<User?> = repo.currentUser
    val sync: StateFlow<SyncState> = repo.sync          // online / offline status for the screens
    val wallet: StateFlow<Wallet?> = repo.wallet         // skill credits (time bank)
    val wants: StateFlow<List<Long>> = repo.wants
    val matches: StateFlow<List<Match>> = repo.matches
    val notices: SharedFlow<String> = repo.notices       // live banner messages
    val activity: StateFlow<List<ActivityLine>> = repo.activity   // live community ticker
    val rooms: StateFlow<Map<Long, RoomPresence>> = repo.rooms     // open video rooms of my lessons (who is waiting)

    // The ongoing-call notification was tapped: AppNavHost opens the call screen, then clears the flag.
    // A flag (not an event) so the request is not lost when it comes before the screens exist (cold start).
    private val _openCall = MutableStateFlow(false)
    val openCall: StateFlow<Boolean> = _openCall.asStateFlow()
    fun requestOpenCall() { _openCall.value = true }
    fun openCallHandled() { _openCall.value = false }
    // -1 / +1 for the flying-coin animation: server events (+1 earned) and my own bookings (-1)
    private val localCoins = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val coins: Flow<Int> = merge(repo.coins, localCoins)
    fun playCoin(amount: Int) { localCoins.tryEmit(amount) }

    /** Every lesson except my own, for the swipe deck. */
    val allOffers: StateFlow<List<OfferCard>> = combine(repo.allOffers(), currentUser) { list, me ->
        list.filter { it.teacherId != me?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // App language: saved choice, else the phone's language (uz / ru), else English
    private val _lang = MutableStateFlow(Lang.fromCode(repo.savedLanguage() ?: Locale.getDefault().language))
    val lang: StateFlow<Lang> = _lang.asStateFlow()
    fun setLanguage(l: Lang) {
        _lang.value = l
        repo.saveLanguage(l.code)
    }

    // Confetti: every +1 plays the animation once
    private val _celebrate = MutableStateFlow(0)
    val celebrate: StateFlow<Int> = _celebrate.asStateFlow()
    fun celebrate() { _celebrate.value += 1 }

    private var liveJob: Job? = null

    /** Open the live connection (does nothing for phone-only accounts or if it is already open). */
    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch { repo.runLiveUpdates() }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    // search + filter inputs from the Browse screen
    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)

    // PERFORMANCE: the search runs when typing pauses (150 ms), not once per letter. Each search re-sorts
    // and re-animates the whole list, which made fast typing stutter.
    val offers: StateFlow<List<OfferCard>> =
        combine(query.debounce { if (it.isEmpty()) 0L else 150L }, category) { q, c -> q.trim() to c }
            .distinctUntilChanged()
            .flatMapLatest { (q, c) -> repo.offers(q, c) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val skills: StateFlow<List<Skill>> = repo.skills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myOffers: StateFlow<List<OfferCard>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.myOffers(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val learning: StateFlow<List<BookingLine>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.learning(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val teaching: StateFlow<List<BookingLine>> = currentUser
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.teaching(u.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.restoreSession()
            _ready.value = true
            repo.refresh()          // online account: download the newest data in the background
            startLive()
        }
        viewModelScope.launch { repo.wakeServer() }
    }

    fun offer(id: Long): Flow<OfferCard?> = repo.offer(id)
    fun slots(offerId: Long): Flow<List<TimeSlot>> = repo.slots(offerId)
    fun reviews(offerId: Long): Flow<List<ReviewLine>> = repo.reviews(offerId)

    // onResult(null) = success, onResult("text") = error message to show
    fun login(email: String, password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val error = runCatching { repo.login(email, password).getOrThrow() }.exceptionOrNull()?.message
        if (error == null) startLive()
        onResult(error)
    }

    // Sign in with Google: the button appears only when the server has a Google client id.
    // Continue with Telegram: the button appears only when the server's bot is set up.
    private val _googleClientId = MutableStateFlow<String?>(null)
    val googleClientId: StateFlow<String?> = _googleClientId.asStateFlow()
    private val _telegramLogin = MutableStateFlow(false)
    val telegramLogin: StateFlow<Boolean> = _telegramLogin.asStateFlow()
    fun loadGoogleConfig() {
        if (_googleClientId.value != null && _telegramLogin.value) return
        viewModelScope.launch {
            val config = repo.authConfig()
            if (config.googleClientId != null) _googleClientId.value = config.googleClientId
            if (config.telegramLogin) _telegramLogin.value = true
        }
    }
    init { loadGoogleConfig() }

    // ---------- Continue with Telegram ----------
    // 1. ask the server for a one-time link  2. the screen opens it (Telegram: tap Start)
    // 3. ask the server every 2 seconds, for at most 5 minutes, whether that happened.
    // The waiting lives here (not in the screen) so it keeps going while the user is in the Telegram app.
    private val _telegramLoginUi = MutableStateFlow(TelegramLoginUi())
    val telegramLoginUi: StateFlow<TelegramLoginUi> = _telegramLoginUi.asStateFlow()
    private var telegramLoginJob: Job? = null

    fun loginWithTelegram() {
        if (telegramLoginJob?.isActive == true) return
        telegramLoginJob = viewModelScope.launch {
            _telegramLoginUi.value = TelegramLoginUi(waiting = true)
            try {
                val start = repo.telegramLoginStart()
                _telegramLoginUi.value = TelegramLoginUi(waiting = true, link = start.url, openLink = true)
                val deadline = System.currentTimeMillis() + start.expiresIn.coerceIn(30, 300) * 1000L
                while (true) {
                    delay(2_000)
                    // a short network problem (e.g. while switching apps) is not an error: ask again
                    val status = try { repo.telegramLoginCheck(start.token) } catch (e: CancellationException) { throw e } catch (e: Exception) { "pending" }
                    if (status == "confirmed") {
                        startLive()
                        _telegramLoginUi.value = TelegramLoginUi(done = true)
                        return@launch
                    }
                    if (status == "expired" || System.currentTimeMillis() > deadline) {
                        _telegramLoginUi.value = TelegramLoginUi(expired = true)
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _telegramLoginUi.value = TelegramLoginUi(error = e.message ?: "Telegram login is not available")
            }
        }
    }

    /** The screen opened the link: do not open it again after a rotation. */
    fun telegramLinkOpened() {
        _telegramLoginUi.value = _telegramLoginUi.value.copy(openLink = false)
    }

    /** Cancel was tapped, or the screen has moved on after a successful login. */
    fun resetTelegramLogin() {
        telegramLoginJob?.cancel()
        telegramLoginJob = null
        _telegramLoginUi.value = TelegramLoginUi()
    }

    // ---------- @username ----------
    // "Pick your @username": once per account, right after the first login, while the username is still empty.
    private val usernameSkipped = MutableStateFlow<Set<Long>>(emptySet())
    val needsUsername: StateFlow<Boolean> = combine(currentUser, usernameSkipped) { u, skipped ->
        u != null && u.username != null && u.username.isBlank() && u.id !in skipped && !repo.usernamePromptSeen(u.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun skipUsername() {
        val id = currentUser.value?.id ?: return
        repo.markUsernamePromptSeen(id)
        usernameSkipped.value = usernameSkipped.value + id
    }

    fun saveUsername(username: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val error = runCatching { repo.saveUsername(username).getOrThrow() }.exceptionOrNull()?.message
        if (error == null) currentUser.value?.id?.let { repo.markUsernamePromptSeen(it) }
        onResult(error)
    }

    /** Is this @username free? Null = the server could not be asked. */
    suspend fun usernameAvailable(username: String): UsernameCheck? = repo.usernameAvailable(username)

    fun saveProfile(edit: ProfileEdit, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.saveProfile(edit).getOrThrow() }.exceptionOrNull()?.message)
    }

    /** One person from the cache (their @username and, if the server allowed it, their contacts). */
    fun person(id: Long): Flow<User?> = repo.person(id)

    fun loginWithGoogle(idToken: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val error = runCatching { repo.loginWithGoogle(idToken).getOrThrow() }.exceptionOrNull()?.message
        if (error == null) startLive()
        onResult(error)
    }

    fun refresh() = viewModelScope.launch { repo.refresh() }

    /** The app came back to the screen (e.g. after switching apps). */
    fun onAppResumed() {
        if (!_ready.value) return
        viewModelScope.launch { repo.refreshIfStale() }
        startLive()
    }

    /** The app went to the background: close the live connection to save battery. */
    fun onAppPaused() = stopLive()

    fun register(name: String, email: String, password: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            val error = runCatching { repo.register(name, email, password).getOrThrow() }.exceptionOrNull()?.message
            if (error == null) startLive()
            onResult(error)
        }

    fun logout() {
        stopLive()
        viewModelScope.launch { repo.logout() }
    }

    /** End of a swipe session: the skills swiped right become wishes, so Perfect matches update. */
    fun saveLiked(skillIds: List<Long>) {
        val merged = uz.skillswap.app.util.mergeWants(wants.value, skillIds)
        if (merged.toSet() != wants.value.toSet()) setWants(merged) { }
    }

    // Badges + leaderboard. A badge unlocked since the last look plays the "Badge unlocked!" moment once.
    private val _achievements = MutableStateFlow<Achievements?>(null)
    val achievements: StateFlow<Achievements?> = _achievements.asStateFlow()
    private val _badgeUnlocked = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val badgeUnlocked: SharedFlow<String> = _badgeUnlocked.asSharedFlow()

    fun loadAchievements() = viewModelScope.launch {
        val a = runCatching { repo.achievements() }.getOrNull() ?: return@launch
        _achievements.value = a
        val uid = currentUser.value?.id ?: return@launch
        val unlocked = a.badges.filter { it.unlocked }.map { it.id }.toSet()
        val seen = repo.seenBadges(uid)
        if (seen != null) (unlocked - seen).forEach { _badgeUnlocked.tryEmit(it) }
        repo.saveSeenBadges(uid, unlocked + (seen ?: emptySet()))
    }

    init {
        // every fresh download may have unlocked something (my lesson, someone's 5-star review...)
        viewModelScope.launch {
            sync.map { it.lastSync }.distinctUntilChanged().collect { t ->
                if (t > 0 && currentUser.value != null) loadAchievements()
            }
        }
    }

    // "Level up!": finished lessons (learned + taught) moved me to a higher member level.
    // Only after real data is in (lastSync > 0), and the first value ever is stored silently,
    // so logging in on a new phone does not celebrate an old level.
    private val _levelUp = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val levelUp: SharedFlow<String> = _levelUp.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(currentUser, sync) { u, s -> if (s.lastSync > 0L) u?.id else null }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf<Pair<Long, Int>?>(null)
                    else combine(repo.learning(uid), repo.teaching(uid)) { l, t ->
                        uid to (l.count { it.status == STATUS_DONE } + t.count { it.status == STATUS_DONE })
                    }
                }
                .distinctUntilChanged()
                .collect { pair ->
                    val (uid, done) = pair ?: return@collect
                    val level = memberLevel(done)
                    val seen = repo.seenLevel(uid)
                    if (seen != null && levelRank(level) > levelRank(seen)) _levelUp.tryEmit(level)
                    if (seen == null || levelRank(level) > levelRank(seen)) repo.saveSeenLevel(uid, level)
                }
        }
    }

    private fun levelRank(level: String) = listOf("Newcomer", "Bronze", "Silver", "Gold").indexOf(level)

    // Telegram bot (Profile card)
    private val _telegram = MutableStateFlow<TelegramStatus?>(null)
    val telegram: StateFlow<TelegramStatus?> = _telegram.asStateFlow()

    fun loadTelegram() = viewModelScope.launch {
        runCatching { repo.telegramStatus() }.onSuccess { _telegram.value = it }
    }

    /** onResult(link, error): the link opens Telegram, where the user taps Start. */
    fun connectTelegram(onResult: (String?, String?) -> Unit) = viewModelScope.launch {
        runCatching { repo.telegramLink() }
            .onSuccess { onResult(it, null) }
            .onFailure { onResult(null, it.message ?: "Telegram is not available") }
    }

    fun disconnectTelegram() = viewModelScope.launch {
        runCatching { repo.telegramUnlink() }
        loadTelegram()
    }

    /** AI matchmaker; onResult(result, error). Answers come in the app's language. */
    fun askMatchmaker(text: String, onResult: (MatchResult?, String?) -> Unit) = viewModelScope.launch {
        runCatching { repo.match(text, lang.value.code) }
            .onSuccess { onResult(it, null) }
            .onFailure { onResult(null, it.message ?: "Could not find a match") }
    }

    // Skill Galaxy screen
    private val _galaxy = MutableStateFlow<Galaxy?>(null)
    val galaxy: StateFlow<Galaxy?> = _galaxy.asStateFlow()
    private val _galaxyError = MutableStateFlow<String?>(null)
    val galaxyError: StateFlow<String?> = _galaxyError.asStateFlow()

    fun loadGalaxy() = viewModelScope.launch {
        runCatching { repo.galaxy() }
            .onSuccess { _galaxy.value = it; _galaxyError.value = null }
            .onFailure { _galaxyError.value = it.message ?: "Could not load the galaxy" }
    }

    // Community impact screen
    private val _impact = MutableStateFlow<Impact?>(null)
    val impact: StateFlow<Impact?> = _impact.asStateFlow()
    private val _impactError = MutableStateFlow<String?>(null)
    val impactError: StateFlow<String?> = _impactError.asStateFlow()

    fun loadImpact() = viewModelScope.launch {
        runCatching { repo.impact() }
            .onSuccess { _impact.value = it; _impactError.value = null }
            .onFailure { _impactError.value = it.message ?: "Could not load the numbers" }
    }

    fun setWants(skillIds: List<Long>, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.setWants(skillIds) }.exceptionOrNull()?.message)
    }

    /** onResult(plan, source, error) */
    fun lessonPlan(bookingId: Long, onResult: (String?, String?, String?) -> Unit) = viewModelScope.launch {
        runCatching { repo.lessonPlan(bookingId) }
            .onSuccess { (plan, source) -> onResult(plan, source, null) }
            .onFailure { onResult(null, null, it.message ?: "Could not make a plan") }
    }

    fun updateProfile(name: String, bio: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.updateProfile(name, bio).getOrThrow() }.exceptionOrNull()?.message)
    }

    fun book(slotId: Long, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.book(slotId) }.exceptionOrNull()?.message)
    }

    fun cancel(bookingId: Long, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.cancel(bookingId) }.exceptionOrNull()?.message)
    }

    fun complete(bookingId: Long, code: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.complete(bookingId, code) }.exceptionOrNull()?.message)
    }

    fun review(bookingId: Long, rating: Int, comment: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            onResult(runCatching { repo.review(bookingId, rating, comment) }.exceptionOrNull()?.message)
        }

    fun createOffer(
        skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>,
        onResult: (String?) -> Unit
    ) = viewModelScope.launch {
        onResult(runCatching { repo.createOffer(skillId, level, description, slots) }.exceptionOrNull()?.message)
    }

    /** What the "Continue with Telegram" button shows. */
    data class TelegramLoginUi(
        val waiting: Boolean = false,       // the link is open, we are asking the server
        val link: String? = null,           // the t.me link (also shown as "Open Telegram again")
        val openLink: Boolean = false,      // true once: the screen should open [link] now
        val expired: Boolean = false,       // 5 minutes passed without Start
        val error: String? = null,
        val done: Boolean = false           // logged in: the screen moves on
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkillSwapApp
                SkillSwapViewModel(app.repository)
            }
        }
    }
}
