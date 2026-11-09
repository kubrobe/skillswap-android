package uz.skillswap.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, Skill::class, Offer::class, TimeSlot::class, Booking::class, Review::class],
    version = 4,              // 4: online-only, Room is a cache of the server; 3: reviews belong to offers
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun skillDao(): SkillDao
    abstract fun offerDao(): OfferDao
    abstract fun bookingDao(): BookingDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Singleton: the whole app shares ONE database object
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "skillswap.db"
                )
                    // during development: if you change an @Entity, increase "version"
                    // and the old database is deleted and re-created
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
