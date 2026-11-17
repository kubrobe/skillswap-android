package uz.skillswap.app.call

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.EglBase
import org.webrtc.VideoTrack

/*
 * The seams between the call logic (VideoRoomManager) and the phone:
 *   CallMedia    - my camera/microphone + making connections to other people (WebRTC peer-to-peer today;
 *                  an SFU for group lessons would be another CallMedia, with the same PeerLink idea per person)
 *   AudioRouting - speaker / earpiece / headset / Bluetooth
 *   CallHost     - the foreground service + notification while a call runs
 * Unit tests replace all three with fakes, so the whole call flow is tested without a camera or a server.
 */

/** Our own small copy of WebRTC's ICE states, so the call logic does not depend on WebRTC classes. */
enum class IceState { NEW, CHECKING, CONNECTED, DISCONNECTED, FAILED, CLOSED }

/** The connection to one other person. */
interface PeerLink {
    val remoteUserId: Long
    /** true = I make offers and ICE restarts for this connection (I was in the room first). */
    val offerer: Boolean
    val isConnected: Boolean
    suspend fun makeOffer(iceRestart: Boolean = false)
    suspend fun acceptOffer(sdp: String)
    suspend fun acceptAnswer(sdp: String)
    fun addRemoteCandidate(c: SignalPayload.Candidate)
    /** Send this picture instead of the current one (camera <-> screen). Same connection: no new offer needed. */
    fun setOutgoingVideo(track: VideoTrack?) {}
    fun close()
}

interface PeerEvents {
    fun onRemoteVideo(link: PeerLink, track: VideoTrack?)
    fun onIceState(link: PeerLink, state: IceState)
    fun onNegotiationError(link: PeerLink, message: String)
}

/** My camera and microphone for one call, and the factory for connections. */
interface CallMedia {
    val hasMic: Boolean
    val hasCamera: Boolean
    val micEnabled: Boolean
    val cameraEnabled: Boolean
    val frontCamera: Boolean
    val canSwitchCamera: Boolean
    val localVideo: VideoTrack?
    /** For drawing video on screen (null in tests). */
    val eglContext: EglBase.Context?
    fun setMicEnabled(on: Boolean)
    fun setCameraEnabled(on: Boolean)
    fun switchCamera(done: (Boolean) -> Unit)

    // ---- "Demonstrate": send my phone screen instead of my camera ----
    /** true while my screen is sent instead of my camera. */
    val sharingScreen: Boolean get() = false
    /** The picture the connection should send now: the screen while demonstrating, otherwise the camera. */
    val outgoingVideo: VideoTrack? get() = localVideo
    /**
     * Starts capturing the screen with the permission Android's "Start recording or casting?" dialog returned.
     * [onStopped] runs (on the main thread) when Android stops it (e.g. "Stop sharing" in the status bar).
     * false = it could not start; nothing changed.
     */
    fun startScreenShare(permission: Intent, onStopped: () -> Unit): Boolean = false
    /** Back to the camera. [beforeRelease] must move every connection to [outgoingVideo] (the camera again). */
    fun stopScreenShare(beforeRelease: () -> Unit) {}
    fun connectTo(
        remoteUserId: Long, offerer: Boolean, iceServers: List<IceServerConfig>, scope: CoroutineScope,
        send: (SignalPayload) -> Unit, events: PeerEvents
    ): PeerLink
    /** Frees the camera, microphone and everything native. Call after every PeerLink was closed. */
    fun release()
}

/** Where the other person's voice comes out. */
enum class AudioRoute { SPEAKER, EARPIECE, HEADSET, BLUETOOTH }

interface AudioRouting {
    val routes: StateFlow<List<AudioRoute>>
    val route: StateFlow<AudioRoute>
    fun start()
    fun select(route: AudioRoute)
    fun stop()
}

/** Texts for the ongoing-call notification, in the app's language. */
data class CallLabels(val title: String, val text: String, val hangUp: String)

interface CallHost {
    /** A call started: show the ongoing notification / keep the app alive in the background. */
    fun onCallActive(labels: CallLabels, camera: Boolean, microphone: Boolean)
    fun onCallInactive()
    /**
     * Demonstrating starts ([on] = true): Android 14+ needs the foreground service to declare screen capture
     * BEFORE capturing begins, so [ready] runs only after that (with false if Android refused).
     * [on] = false: capture ended, the service goes back to camera + microphone.
     */
    fun onScreenShare(on: Boolean, ready: (Boolean) -> Unit = {}) = ready(true)
}
