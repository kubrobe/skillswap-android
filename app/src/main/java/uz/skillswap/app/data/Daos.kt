package uz.skillswap.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// A DAO (Data Access Object) lists the SQL queries the app can run.
// Flow<...> = live data: the UI updates automatically when a table changes.
// suspend   = runs in a coroutine, never blocks the screen.

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Long): User?

    @Query("UPDATE users SET fullName = :name, bio = :bio WHERE id = :id")
    suspend fun updateProfile(id: Long, name: String, bio: String)
}

@Dao
interface SkillDao {
    @Insert
    suspend fun insert(skill: Skill): Long

    @Query("SELECT COUNT(*) FROM skills")
    suspend fun count(): Int

    @Query("SELECT * FROM skills ORDER BY name")
    fun observeAll(): Flow<List<Skill>>

    @Query("SELECT DISTINCT category FROM skills ORDER BY category")
    fun categories(): Flow<List<String>>
}

// The SELECT part shared by the Browse list and "My listings"
private const val OFFER_SELECT = """
    SELECT o.id AS offerId, s.name AS skillName, s.emoji AS emoji, s.category AS category,
           o.level AS level, o.description AS description,
           u.id AS teacherId, u.fullName AS teacherName,
           COALESCE((SELECT AVG(r.rating) FROM reviews r JOIN bookings b ON b.id = r.bookingId
                     WHERE b.offerId = o.id), 0.0) AS rating,
           (SELECT COUNT(*) FROM reviews r JOIN bookings b ON b.id = r.bookingId
            WHERE b.offerId = o.id) AS reviewCount,
           (SELECT COUNT(*) FROM time_slots t WHERE t.offerId = o.id AND t.isBooked = 0) AS freeSlots
    FROM offers o
    JOIN skills s ON s.id = o.skillId
    JOIN users u ON u.id = o.userId
"""

@Dao
abstract class OfferDao {
    @Query(
        OFFER_SELECT + """
        WHERE (:category IS NULL OR s.category = :category)
          AND (s.name LIKE '%' || :query || '%' OR u.fullName LIKE '%' || :query || '%')
        ORDER BY rating DESC, s.name"""
    )
    abstract fun search(query: String, category: String?): Flow<List<OfferCard>>

    @Query(OFFER_SELECT + " WHERE o.id = :offerId")
    abstract fun observeOffer(offerId: Long): Flow<OfferCard?>

    @Query(OFFER_SELECT + " WHERE o.userId = :userId ORDER BY s.name")
    abstract fun observeMine(userId: Long): Flow<List<OfferCard>>

    @Query("SELECT * FROM time_slots WHERE offerId = :offerId ORDER BY day, hour")
    abstract fun observeSlots(offerId: Long): Flow<List<TimeSlot>>

    @Query("SELECT * FROM time_slots WHERE offerId = :offerId ORDER BY day, hour")
    abstract suspend fun slotsOf(offerId: Long): List<TimeSlot>

    @Query(
        """SELECT r.rating AS rating, r.comment AS comment, r.createdAt AS createdAt, u.fullName AS learnerName
           FROM reviews r JOIN bookings b ON b.id = r.bookingId JOIN users u ON u.id = b.learnerId
           WHERE b.offerId = :offerId ORDER BY r.createdAt DESC"""
    )
    abstract fun observeReviews(offerId: Long): Flow<List<ReviewLine>>

    @Query("SELECT COUNT(*) FROM offers WHERE userId = :userId AND skillId = :skillId")
    protected abstract suspend fun countMine(userId: Long, skillId: Long): Int

    @Insert
    protected abstract suspend fun insertOffer(offer: Offer): Long

    @Insert
    protected abstract suspend fun insertSlots(slots: List<TimeSlot>)

    /** Publish a listing: the offer AND its time slots are saved together, or nothing is saved. */
    @Transaction
    open suspend fun createOffer(
        userId: Long, skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>
    ): Long {
        require(slots.isNotEmpty()) { "Add at least one time slot" }
        if (countMine(userId, skillId) > 0) throw IllegalStateException("You already teach this skill")
        val offerId = insertOffer(Offer(userId = userId, skillId = skillId, level = level, description = description))
        insertSlots(slots.distinct().map { TimeSlot(offerId = offerId, day = it.first, hour = it.second) })
        return offerId
    }
}

class SlotTakenException : Exception("Sorry, this time was just booked by someone else")

// The SELECT part shared by "I learn" and "I teach"
private const val BOOKING_SELECT = """
    SELECT b.id AS bookingId, b.status AS status, b.createdAt AS createdAt,
           s.name AS skillName, s.emoji AS emoji,
           tu.fullName AS teacherName, lu.fullName AS learnerName,
           t.day AS day, t.hour AS hour,
           (SELECT r.rating FROM reviews r WHERE r.bookingId = b.id) AS myRating
    FROM bookings b
    JOIN time_slots t ON t.id = b.slotId
    JOIN offers o ON o.id = b.offerId
    JOIN skills s ON s.id = o.skillId
    JOIN users tu ON tu.id = o.userId
    JOIN users lu ON lu.id = b.learnerId
"""

@Dao
abstract class BookingDao {
    @Query(BOOKING_SELECT + " WHERE b.learnerId = :userId ORDER BY b.createdAt DESC")
    abstract fun observeLearning(userId: Long): Flow<List<BookingLine>>

    @Query(BOOKING_SELECT + " WHERE o.userId = :userId ORDER BY b.createdAt DESC")
    abstract fun observeTeaching(userId: Long): Flow<List<BookingLine>>

    @Query("SELECT * FROM time_slots WHERE id = :id")
    protected abstract suspend fun slot(id: Long): TimeSlot?

    @Query("SELECT userId FROM offers WHERE id = :offerId")
    protected abstract suspend fun teacherOf(offerId: Long): Long

    // returns the number of changed rows: 0 means "somebody else already took this slot"
    @Query("UPDATE time_slots SET isBooked = 1 WHERE id = :id AND isBooked = 0")
    protected abstract suspend fun takeSlot(id: Long): Int

    @Query("UPDATE time_slots SET isBooked = 0 WHERE id = :id")
    protected abstract suspend fun freeSlot(id: Long)

    // does this learner already have a lesson at the same weekday and hour?
    @Query(
        """SELECT COUNT(*) FROM bookings b JOIN time_slots t ON t.id = b.slotId
           WHERE b.learnerId = :learnerId AND b.status = 'BOOKED' AND t.day = :day AND t.hour = :hour"""
    )
    protected abstract suspend fun clashes(learnerId: Long, day: Int, hour: Int): Int

    @Insert
    protected abstract suspend fun insertBooking(booking: Booking): Long

    @Query("SELECT * FROM bookings WHERE id = :id")
    protected abstract suspend fun booking(id: Long): Booking?

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    protected abstract suspend fun setStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM reviews WHERE bookingId = :bookingId")
    protected abstract suspend fun reviewCount(bookingId: Long): Int

    @Insert
    protected abstract suspend fun insertReview(review: Review): Long

    /** Book a time slot: ALL steps succeed, or (if an exception is thrown) NOTHING is saved. */
    @Transaction
    open suspend fun book(learnerId: Long, slotId: Long): Long {
        val slot = slot(slotId) ?: throw IllegalArgumentException("This time does not exist any more")
        if (teacherOf(slot.offerId) == learnerId) throw IllegalStateException("You cannot book your own lesson")
        if (clashes(learnerId, slot.day, slot.hour) > 0) {
            throw IllegalStateException("You already have a lesson at this time")
        }
        if (takeSlot(slotId) == 0) throw SlotTakenException()       // rolls back everything
        return insertBooking(Booking(learnerId = learnerId, slotId = slotId, offerId = slot.offerId))
    }

    /** Cancel a lesson and make the time free again for other learners. */
    @Transaction
    open suspend fun cancel(userId: Long, bookingId: Long) {
        val b = booking(bookingId)
        if (b == null || b.learnerId != userId) throw IllegalArgumentException("Booking not found")
        if (b.status != STATUS_BOOKED) throw IllegalStateException("Only a planned lesson can be cancelled")
        setStatus(bookingId, STATUS_CANCELLED)
        freeSlot(b.slotId)
    }

    @Transaction
    open suspend fun complete(userId: Long, bookingId: Long) {
        val b = booking(bookingId)
        if (b == null || b.learnerId != userId) throw IllegalArgumentException("Booking not found")
        if (b.status != STATUS_BOOKED) throw IllegalStateException("Only a planned lesson can be marked as done")
        setStatus(bookingId, STATUS_DONE)
    }

    /** Only the learner, only after the lesson is DONE, and only once per lesson. */
    @Transaction
    open suspend fun addReview(userId: Long, bookingId: Long, rating: Int, comment: String): Long {
        val b = booking(bookingId)
        if (b == null || b.learnerId != userId) throw IllegalArgumentException("Booking not found")
        if (b.status != STATUS_DONE) throw IllegalStateException("You can rate a lesson after it is done")
        if (reviewCount(bookingId) > 0) throw IllegalStateException("You already rated this lesson")
        require(rating in 1..5) { "Rating must be from 1 to 5" }
        return insertReview(Review(bookingId = bookingId, rating = rating, comment = comment.trim()))
    }
}
