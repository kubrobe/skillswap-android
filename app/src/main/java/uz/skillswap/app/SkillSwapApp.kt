package uz.skillswap.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uz.skillswap.app.call.AndroidCallHost
import uz.skillswap.app.call.CallAudioRouter
import uz.skillswap.app.call.LocalMedia
import uz.skillswap.app.call.ServerSignaling
import uz.skillswap.app.call.VideoRoomManager
import uz.skillswap.app.data.AppDatabase
import uz.skillswap.app.data.SkillSwapRepository
import uz.skillswap.app.data.remote.ApiClient

/** Created once when the app starts. Holds objects the whole app shares. */
class SkillSwapApp : Application() {
    val repository: SkillSwapRepository by lazy {
        SkillSwapRepository(
            db = AppDatabase.get(this),
            prefs = getSharedPreferences("session", Context.MODE_PRIVATE),
            api = ApiClient()
        )
    }

    /**
     * The video lesson call. App-wide (not inside a screen), so a call survives turning the phone, leaving the
     * call screen and the app going to the background. Nothing starts until a lesson's video room is opened.
     */
    val videoRooms: VideoRoomManager by lazy {
        VideoRoomManager(
            signaling = ServerSignaling(repository),
            mediaFactory = { audio, video -> LocalMedia(this, audio, video) },
            audio = CallAudioRouter(this),
            host = AndroidCallHost(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            log = { Log.i("VideoRoom", it) }      // events and states only, never SDP, candidates or tokens
        )
    }
}
