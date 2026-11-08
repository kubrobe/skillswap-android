package uz.skillswap.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.skillswap.app.util.Security
import uz.skillswap.app.util.Validators

/** The single place that talks to the database. Screens never use DAOs directly. */
class SkillSwapRepository(
    private val db: AppDatabase,
    private val prefs: SharedPreferences
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

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
    }

    // ---------- accounts ----------
    suspend fun register(fullName: String, email: String, password: String): Result<User> {
        val mail = email.trim().lowercase()
        if (fullName.isBlank()) return Result.failure(IllegalArgumentException("Enter your name"))
        if (!Validators.isValidEmail(mail)) return Result.failure(IllegalArgumentException("Invalid e-mail"))
        if (!Validators.isStrongPassword(password)) {
            return Result.failure(IllegalArgumentException("Password: min 6 characters and 1 digit"))
        }
        if (db.userDao().findByEmail(mail) != null) {
            return Result.failure(IllegalArgumentException("This e-mail is already registered"))
        }
        val user = User(fullName = fullName.trim(), email = mail,
            passwordHash = Security.hashPassword(password, mail))
        val saved = user.copy(id = db.userDao().insert(user))
        startSession(saved)
        return Result.success(saved)
    }

    suspend fun login(email: String, password: String): Result<User> {
        val mail = email.trim().lowercase()
        val user = db.userDao().findByEmail(mail)
        if (user == null || user.passwordHash != Security.hashPassword(password, mail)) {
            return Result.failure(IllegalArgumentException("Wrong e-mail or password"))
        }
        startSession(user)
        return Result.success(user)
    }

    suspend fun updateProfile(name: String, bio: String): Result<Unit> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty"))
        val id = requireUserId()
        db.userDao().updateProfile(id, name.trim(), bio.trim())
        _currentUser.value = db.userDao().findById(id)
        return Result.success(Unit)
    }

    fun logout() {
        prefs.edit().remove(KEY_USER_ID).apply()
        _currentUser.value = null
    }

    private fun startSession(user: User) {
        prefs.edit().putLong(KEY_USER_ID, user.id).apply()
        _currentUser.value = user
    }

    // ---------- catalogue ----------
    fun offers(query: String, category: String?): Flow<List<OfferCard>> =
        db.offerDao().search(query.trim(), category)

    fun categories(): Flow<List<String>> = db.skillDao().categories()
    fun skills(): Flow<List<Skill>> = db.skillDao().observeAll()
    fun offer(id: Long): Flow<OfferCard?> = db.offerDao().observeOffer(id)
    fun slots(offerId: Long): Flow<List<TimeSlot>> = db.offerDao().observeSlots(offerId)
    fun reviews(offerId: Long): Flow<List<ReviewLine>> = db.offerDao().observeReviews(offerId)
    fun myOffers(userId: Long): Flow<List<OfferCard>> = db.offerDao().observeMine(userId)

    suspend fun createOffer(skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>): Long =
        db.offerDao().createOffer(requireUserId(), skillId, level, description.trim(), slots)

    // ---------- bookings ----------
    fun learning(userId: Long): Flow<List<BookingLine>> = db.bookingDao().observeLearning(userId)
    fun teaching(userId: Long): Flow<List<BookingLine>> = db.bookingDao().observeTeaching(userId)

    suspend fun book(slotId: Long): Long = db.bookingDao().book(requireUserId(), slotId)
    suspend fun cancel(bookingId: Long) = db.bookingDao().cancel(requireUserId(), bookingId)
    suspend fun complete(bookingId: Long) = db.bookingDao().complete(requireUserId(), bookingId)
    suspend fun review(bookingId: Long, rating: Int, comment: String): Long =
        db.bookingDao().addReview(requireUserId(), bookingId, rating, comment)

    private fun requireUserId(): Long =
        currentUser.value?.id ?: throw IllegalStateException("Please log in first")

    private companion object {
        const val KEY_USER_ID = "user_id"
    }
}
