package uz.skillswap.app.ui

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
import uz.skillswap.app.data.User

/** Holds screen state and survives screen rotation. Screens call its functions. */
@OptIn(ExperimentalCoroutinesApi::class)
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
    // -1 / +1 for the flying-coin animation: server events (+1 earned) and my own bookings (-1)
    private val localCoins = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val coins: Flow<Int> = merge(repo.coins, localCoins)
    fun playCoin(amount: Int) { localCoins.tryEmit(amount) }

    /** Every lesson except my own, for the swipe deck. */
    val allOffers: StateFlow<List<OfferCard>> = combine(repo.allOffers(), currentUser) { list, me ->
        list.filter { it.teacherId != me?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val offers: StateFlow<List<OfferCard>> =
        combine(query, category) { q, c -> q to c }
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkillSwapApp
                SkillSwapViewModel(app.repository)
            }
        }
    }
}
