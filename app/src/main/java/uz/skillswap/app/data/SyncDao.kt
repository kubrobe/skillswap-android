package uz.skillswap.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Replaces the phone's copy of the data with what the server sent.
 * One @Transaction: the screens never see a half-updated database.
 * Order matters because of the foreign keys: delete children first, insert parents first.
 */
@Dao
abstract class SyncDao {
    @Query("DELETE FROM reviews") protected abstract suspend fun clearReviews()
    @Query("DELETE FROM bookings") protected abstract suspend fun clearBookings()
    @Query("DELETE FROM time_slots") protected abstract suspend fun clearSlots()
    @Query("DELETE FROM offers") protected abstract suspend fun clearOffers()
    @Query("DELETE FROM skills") protected abstract suspend fun clearSkills()
    @Query("DELETE FROM users") protected abstract suspend fun clearUsers()

    @Insert protected abstract suspend fun insertUsers(items: List<User>)
    @Insert protected abstract suspend fun insertSkills(items: List<Skill>)
    @Insert protected abstract suspend fun insertOffers(items: List<Offer>)
    @Insert protected abstract suspend fun insertSlots(items: List<TimeSlot>)
    @Insert protected abstract suspend fun insertBookings(items: List<Booking>)
    @Insert protected abstract suspend fun insertReviews(items: List<Review>)

    @Transaction
    open suspend fun clearAll() {
        clearReviews(); clearBookings(); clearSlots(); clearOffers(); clearSkills(); clearUsers()
    }

    @Transaction
    open suspend fun replaceAll(
        users: List<User>, skills: List<Skill>, offers: List<Offer>,
        slots: List<TimeSlot>, bookings: List<Booking>, reviews: List<Review>
    ) {
        clearAll()
        insertUsers(users)
        insertSkills(skills)
        insertOffers(offers)
        insertSlots(slots)
        insertBookings(bookings)
        insertReviews(reviews)
    }
}
