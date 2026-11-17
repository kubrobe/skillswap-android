package uz.skillswap.app.call

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * The phone's own camera and microphone for one call, plus the WebRTC factory that makes connections.
 * Created when a call starts, [release]d when it ends, so the camera and microphone are never held after a call.
 * All methods are called on the main thread by [VideoRoomManager].
 */
class LocalMedia(context: Context, withAudio: Boolean, withVideo: Boolean) : CallMedia {
    private val app = context.applicationContext

    val eglBase: EglBase = EglBase.create()
    val factory: PeerConnectionFactory

    private val audioSource: AudioSource?
    val audioTrack: AudioTrack?

    private var videoSource: VideoSource? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var capturer: CameraVideoCapturer? = null
    var videoTrack: VideoTrack? = null
        private set

    private val enumerator: CameraEnumerator =
        if (Camera2Enumerator.isSupported(app)) Camera2Enumerator(app) else Camera1Enumerator(true)
    private var cameraName: String? = null

    /** Is the camera currently sending pictures (on, and not paused for the background). */
    var capturing = false
        private set
    override var frontCamera = true
        private set
    override val hasMic: Boolean get() = audioTrack != null
    override val hasCamera: Boolean get() = videoTrack != null
    override val canSwitchCamera: Boolean get() = enumerator.deviceNames.size > 1
    /** My small self-view: the camera; nothing while demonstrating (a picture of the screen inside the screen). */
    override val localVideo: VideoTrack? get() = if (screenTrack != null) null else videoTrack
    override val outgoingVideo: VideoTrack? get() = screenTrack ?: videoTrack
    override val eglContext: EglBase.Context get() = eglBase.eglBaseContext
    override val cameraEnabled: Boolean get() = videoTrack != null && capturing

    override fun connectTo(
        remoteUserId: Long, offerer: Boolean, iceServers: List<IceServerConfig>, scope: CoroutineScope,
        send: (SignalPayload) -> Unit, events: PeerEvents
    ): PeerLink = PeerSession(remoteUserId, offerer, this, iceServers, scope, send, events)

    init {
        initWebRtcOnce(app)
        val adm = JavaAudioDeviceModule.builder(app)
            .setUseHardwareAcousticEchoCanceler(true)       // phone's own echo cancelling (no feedback on speaker)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(msg: String?) { Log.e(TAG, "mic init error: $msg") }
                override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode?, msg: String?) {
                    Log.e(TAG, "mic start error: $code $msg")
                }
                override fun onWebRtcAudioRecordError(msg: String?) { Log.e(TAG, "mic error: $msg") }
            })
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        adm.release()                                       // the factory keeps its own reference

        if (withAudio) {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("skillswap-audio", audioSource)
        } else {
            audioSource = null
            audioTrack = null
        }
        if (withVideo) startVideo()
    }

    private fun startVideo() {
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
        if (name == null) {
            Log.w(TAG, "no camera on this phone")
            return
        }
        val cap = enumerator.createCapturer(name, null) ?: return
        val source = factory.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("skillswap-capture", eglBase.eglBaseContext)
        cap.initialize(helper, app, source.capturerObserver)
        cameraName = name
        frontCamera = enumerator.isFrontFacing(name)
        capturer = cap
        videoSource = source
        textureHelper = helper
        videoTrack = factory.createVideoTrack("skillswap-video", source)
        startCapture()
    }

    private fun startCapture() {
        val cap = capturer ?: return
        if (capturing) return
        try {
            cap.startCapture(WIDTH, HEIGHT, FPS)
            capturing = true
        } catch (e: RuntimeException) {
            Log.e(TAG, "camera start failed: ${e.message}")
        }
    }

    private fun stopCapture() {
        val cap = capturer ?: return
        if (!capturing) return
        try {
            cap.stopCapture()            // frees the camera (and its light) for other apps
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        capturing = false
    }

    /** Microphone on/off. Off = silence is sent; the microphone stays ready, so on is instant. */
    override fun setMicEnabled(on: Boolean) {
        audioTrack?.setEnabled(on)
    }

    override val micEnabled: Boolean get() = audioTrack?.enabled() == true

    /** Camera on/off. Off really stops the camera, not only the picture. */
    override fun setCameraEnabled(on: Boolean) {
        val track = videoTrack ?: return
        if (on) startCapture() else stopCapture()
        track.setEnabled(on)
    }

    /** Front <-> back camera. [done] gets true when it switched. */
    override fun switchCamera(done: (Boolean) -> Unit) {
        val cap = capturer ?: return done(false)
        cap.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                frontCamera = isFrontCamera
                done(true)
            }
            override fun onCameraSwitchError(error: String?) {
                Log.w(TAG, "camera switch failed: $error")
                done(false)
            }
        })
    }

    // ---------------- "Demonstrate": the phone screen instead of the camera ----------------

    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenSource: VideoSource? = null
    private var screenHelper: SurfaceTextureHelper? = null
    private var screenTrack: VideoTrack? = null
    private var cameraWasOn = false
    override val sharingScreen: Boolean get() = screenTrack != null

    override fun startScreenShare(permission: Intent, onStopped: () -> Unit): Boolean {
        if (released) return false
        if (screenTrack != null) return true
        val main = Handler(Looper.getMainLooper())
        val cap = ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
            override fun onStop() { main.post { onStopped() } }     // "Stop sharing" in Android's status bar
        })
        val source = factory.createVideoSource(true)                // true = screen content (sharp text, fewer frames)
        val helper = SurfaceTextureHelper.create("skillswap-screen", eglBase.eglBaseContext)
        return try {
            cap.initialize(helper, app, source.capturerObserver)
            val (w, h) = screenSize()
            cap.startCapture(w, h, SCREEN_FPS)
            cameraWasOn = capturing
            stopCapture()                                           // the camera rests while the screen is sent
            screenCapturer = cap
            screenSource = source
            screenHelper = helper
            screenTrack = factory.createVideoTrack("skillswap-screen", source)
            true
        } catch (e: RuntimeException) {                             // e.g. Android refused the projection
            Log.e(TAG, "screen share failed: ${e.message}")
            runCatching { cap.dispose() }
            source.dispose()
            helper.dispose()
            false
        }
    }

    override fun stopScreenShare(beforeRelease: () -> Unit) {
        val track = screenTrack ?: return
        screenTrack = null                     // outgoingVideo is the camera again ...
        if (cameraWasOn) startCapture()
        beforeRelease()                        // ... and every connection sends it before the screen track goes away
        try {
            screenCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        screenCapturer?.dispose()
        screenCapturer = null
        track.dispose()
        screenSource?.dispose()
        screenSource = null
        screenHelper?.dispose()
        screenHelper = null
    }

    /** The phone's screen, scaled so the long side is at most [SCREEN_MAX] (even numbers, as encoders like). */
    private fun screenSize(): Pair<Int, Int> {
        val dm = app.resources.displayMetrics
        val long = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val scale = minOf(1f, SCREEN_MAX.toFloat() / long)
        fun even(v: Int) = ((v * scale).toInt() / 2 * 2).coerceAtLeast(2)
        return even(dm.widthPixels) to even(dm.heightPixels)
    }

    /** Frees everything. Call after every PeerConnection using [factory] was disposed. Safe to call twice. */
    override fun release() {
        if (released) return
        released = true
        cameraWasOn = false
        stopScreenShare {}
        stopCapture()
        capturer?.dispose()
        capturer = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        textureHelper?.dispose()
        textureHelper = null
        audioTrack?.dispose()
        audioSource?.dispose()
        factory.dispose()
        eglBase.release()
    }

    private var released = false

    companion object {
        private const val TAG = "VideoRoom"
        private const val WIDTH = 1280
        private const val HEIGHT = 720
        private const val FPS = 30
        private const val SCREEN_MAX = 1280
        private const val SCREEN_FPS = 15
        @Volatile private var initialized = false

        /** WebRTC's native library is loaded once per app process. */
        private fun initWebRtcOnce(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
                )
                initialized = true
            }
        }
    }
}
