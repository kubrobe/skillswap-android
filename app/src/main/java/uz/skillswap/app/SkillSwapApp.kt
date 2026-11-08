package uz.skillswap.app

import android.app.Application
import android.content.Context
import uz.skillswap.app.data.AppDatabase
import uz.skillswap.app.data.SkillSwapRepository

/** Created once when the app starts. Holds objects the whole app shares. */
class SkillSwapApp : Application() {
    val repository: SkillSwapRepository by lazy {
        SkillSwapRepository(
            db = AppDatabase.get(this),
            prefs = getSharedPreferences("session", Context.MODE_PRIVATE)
        )
    }
}
