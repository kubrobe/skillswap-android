package uz.skillswap.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puts the phone in "call" audio mode for the lesson and picks the output: Bluetooth > headphones > speaker
 * (a video call is usually held in front of you, so the loudspeaker is the default, like WhatsApp video calls).
 * When headphones are plugged in or Bluetooth connects during the call, the sound moves there by itself.
 * [stop] puts everything back as it was.
 */
class CallAudioRouter(context: Context) : AudioRouting {
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())

    private val _routes = MutableStateFlow<List<AudioRoute>>(emptyList())
    override val routes: StateFlow<List<AudioRoute>> = _routes.asStateFlow()
    private val _route = MutableStateFlow(AudioRoute.SPEAKER)
    override val route: StateFlow<AudioRoute> = _route.asStateFlow()

    private var running = false
    private var savedMode = AudioManager.MODE_NORMAL
    private var savedSpeaker = false
    private var focusRequest: AudioFocusRequest? = null
    private var userChose = false            // the person picked a route: do not override it on device changes

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh(newDevice = true)
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh(newDevice = false)
    }

    override fun start() {
        if (running) return
        running = true
        savedMode = am.mode
        @Suppress("DEPRECATION")
        savedSpeaker = am.isSpeakerphoneOn
        requestFocus()
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.registerAudioDeviceCallback(deviceCallback, main)
        refresh(newDevice = true)
    }

    override fun select(route: AudioRoute) {
        if (!running || route !in _routes.value) return
        userChose = true
        apply(route)
    }

    override fun stop() {
        if (!running) return
        running = false
        userChose = false
        am.unregisterAudioDeviceCallback(deviceCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            run {
                if (am.isBluetoothScoOn) { am.stopBluetoothSco(); am.isBluetoothScoOn = false }
                am.isSpeakerphoneOn = savedSpeaker
            }
        }
        am.mode = savedMode
        abandonFocus()
    }

    private fun refresh(newDevice: Boolean) {
        if (!running) return
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        val list = buildList {
            if (outputs.any { it in BLUETOOTH_TYPES }) add(AudioRoute.BLUETOOTH)
            if (outputs.any { it in HEADSET_TYPES }) add(AudioRoute.HEADSET)
            add(AudioRoute.SPEAKER)
            if (AudioDeviceInfo.TYPE_BUILTIN_EARPIECE in outputs) add(AudioRoute.EARPIECE)
        }
        _routes.value = list
        val current = _route.value
        val target = when {
            // something new was plugged in: use it (unless the person chose a route since then)
            newDevice && !userChose -> list.first()
            current !in list -> list.first()             // the device in use went away
            else -> current
        }
        if (newDevice) userChose = false
        apply(target)
    }

    private fun apply(route: AudioRoute) {
        _route.value = route
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wanted = when (route) {
                    AudioRoute.BLUETOOTH -> BLUETOOTH_TYPES
                    AudioRoute.HEADSET -> HEADSET_TYPES
                    AudioRoute.SPEAKER -> setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    AudioRoute.EARPIECE -> setOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type in wanted }
                if (device != null) am.setCommunicationDevice(device) else Log.w(TAG, "audio route $route not available")
            } else {
                @Suppress("DEPRECATION")
                when (route) {
                    AudioRoute.BLUETOOTH -> { am.isSpeakerphoneOn = false; am.startBluetoothSco(); am.isBluetoothScoOn = true }
                    AudioRoute.SPEAKER -> { stopSco(); am.isSpeakerphoneOn = true }
                    AudioRoute.EARPIECE, AudioRoute.HEADSET -> { stopSco(); am.isSpeakerphoneOn = false }
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "audio route $route failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun stopSco() {
        if (am.isBluetoothScoOn) { am.isBluetoothScoOn = false; am.stopBluetoothSco() }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private companion object {
        const val TAG = "VideoRoom"
        val BLUETOOTH_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val HEADSET_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) add(AudioDeviceInfo.TYPE_USB_HEADSET)
        }
    }
}
