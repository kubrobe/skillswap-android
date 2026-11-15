package uz.skillswap.app.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.StateFlow
import uz.skillswap.app.SkillSwapApp
import uz.skillswap.app.call.AudioRoute
import uz.skillswap.app.call.CallLabels
import uz.skillswap.app.call.CallUiState
import uz.skillswap.app.call.VideoRoomManager

/**
 * The call screen's ViewModel. The call itself lives in the app-wide [VideoRoomManager] (it must outlive this
 * screen); this only passes the screen's actions on and exposes the state.
 */
class VideoRoomViewModel(private val calls: VideoRoomManager) : ViewModel() {
    val state: StateFlow<CallUiState> = calls.state

    fun join(bookingId: Long, audio: Boolean, video: Boolean, labels: CallLabels) = calls.join(bookingId, audio, video, labels)
    fun toggleMic() = calls.toggleMic()
    fun toggleCamera() = calls.toggleCamera()
    fun switchCamera() = calls.switchCamera()
    fun selectAudioRoute(route: AudioRoute) = calls.selectAudioRoute(route)
    fun hangUp(forBoth: Boolean) = calls.hangUp(forBoth)
    fun retry() = calls.retry()
    fun dismiss() = calls.dismiss()
    fun clearBusyError() = calls.clearBusyError()

    companion object {
        val Factory = viewModelFactory {
            initializer { VideoRoomViewModel((this[APPLICATION_KEY] as SkillSwapApp).videoRooms) }
        }
    }
}
