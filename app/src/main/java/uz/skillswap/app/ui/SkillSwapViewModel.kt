package uz.skillswap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import uz.skillswap.app.data.User

/** Holds screen state and survives screen rotation. Screens call its functions. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillSwapViewModel(private val repo: SkillSwapRepository) : ViewModel() {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()     // true after start-up work

    val currentUser: StateFlow<User?> = repo.currentUser
    val sync: StateFlow<SyncState> = repo.sync          // online / offline status for the screens

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
            repo.seedIfEmpty()
            repo.restoreSession()
            _ready.value = true
            repo.refresh()          // online account: download the newest data in the background
        }
    }

    fun offer(id: Long): Flow<OfferCard?> = repo.offer(id)
    fun slots(offerId: Long): Flow<List<TimeSlot>> = repo.slots(offerId)
    fun reviews(offerId: Long): Flow<List<ReviewLine>> = repo.reviews(offerId)

    // onResult(null) = success, onResult("text") = error message to show
    fun login(email: String, password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.login(email, password).getOrThrow() }.exceptionOrNull()?.message)
    }

    fun refresh() = viewModelScope.launch { repo.refresh() }

    fun register(name: String, email: String, password: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            onResult(runCatching { repo.register(name, email, password).getOrThrow() }.exceptionOrNull()?.message)
        }

    fun logout() {
        viewModelScope.launch { repo.logout() }
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

    fun complete(bookingId: Long, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(runCatching { repo.complete(bookingId) }.exceptionOrNull()?.message)
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
