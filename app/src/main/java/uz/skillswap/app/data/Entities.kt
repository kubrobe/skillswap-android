package uz.skillswap.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Each @Entity class becomes one table in the SQLite database.
// Relations: Offer -> User, Offer -> Skill, TimeSlot -> Offer, Booking -> User/TimeSlot/Offer, Review -> Offer

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val email: String,
    val passwordHash: String,          // never store the plain password!
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** A skill in the catalogue, e.g. "Python basics" (category "Tech"). */
@Entity(tableName = "skills", indices = [Index(value = ["name"], unique = true)])
data class Skill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val emoji: String
)

/** "User X teaches skill Y at this level". One user can teach a skill only once. */
@Entity(
    tableName = "offers",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Skill::class, parentColumns = ["id"], childColumns = ["skillId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("skillId"), Index(value = ["userId", "skillId"], unique = true)]
)
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val skillId: Long,
    val level: String,                 // Beginner / Intermediate / Advanced
    val description: String
)

/** A weekly time the teacher is free. day: 1 = Monday ... 7 = Sunday. */
@Entity(
    tableName = "time_slots",
    foreignKeys = [ForeignKey(entity = Offer::class, parentColumns = ["id"], childColumns = ["offerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["offerId", "day", "hour"], unique = true)]
)
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val offerId: Long,
    val day: Int,
    val hour: Int,
    val isBooked: Boolean = false
)

/** A learner reserved one time slot. status: BOOKED, DONE or CANCELLED. */
@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["id"], childColumns = ["learnerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TimeSlot::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Offer::class, parentColumns = ["id"], childColumns = ["offerId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("learnerId"), Index("slotId"), Index("offerId")]
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val learnerId: Long,
    val slotId: Long,
    val offerId: Long,
    val status: String = STATUS_BOOKED,
    val createdAt: Long = System.currentTimeMillis()
)

const val STATUS_BOOKED = "BOOKED"
const val STATUS_DONE = "DONE"
const val STATUS_CANCELLED = "CANCELLED"

/**
 * One review per finished booking (unique index on bookingId).
 * It points to the OFFER and keeps the learner's first name, so the phone can show public
 * reviews without downloading other people's bookings (privacy).
 */
@Entity(
    tableName = "reviews",
    foreignKeys = [ForeignKey(entity = Offer::class, parentColumns = ["id"], childColumns = ["offerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["bookingId"], unique = true), Index("offerId")]
)
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val offerId: Long,
    val learnerName: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ---- Not tables: results of JOIN queries (column aliases in the SQL = property names here) ----

/** One row of the Browse list. */
data class OfferCard(
    val offerId: Long,
    val skillName: String,
    val emoji: String,
    val category: String,
    val level: String,
    val description: String,
    val teacherId: Long,
    val teacherName: String,
    val rating: Double,
    val reviewCount: Int,
    val freeSlots: Int
)

/** One row of "My lessons". */
data class BookingLine(
    val bookingId: Long,
    val status: String,
    val createdAt: Long,
    val skillName: String,
    val emoji: String,
    val teacherName: String,
    val learnerName: String,
    val day: Int,
    val hour: Int,
    val myRating: Int?
)

data class ReviewLine(
    val rating: Int,
    val comment: String,
    val createdAt: Long,
    val learnerName: String
)
