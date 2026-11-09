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
           COALESCE((SELECT ROUND(AVG(r.rating), 1) FROM reviews r WHERE r.offerId = o.id), 0.0) AS rating,
           (SELECT COUNT(*) FROM reviews r WHERE r.offerId = o.id) AS reviewCount,
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
        """SELECT r.rating AS rating, r.comment AS comment, r.createdAt AS createdAt, r.learnerName AS learnerName
           FROM reviews r WHERE r.offerId = :offerId ORDER BY r.createdAt DESC"""
    )
    abstract fun observeReviews(offerId: Long): Flow<List<ReviewLine>>
}

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
}

