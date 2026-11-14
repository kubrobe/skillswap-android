package uz.skillswap.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why listening ended without words. The screen turns each one into a sentence in the user's language. */
enum class VoiceError { NothingHeard, NoInternet, NoPermission, NoLanguage, Failed }

/**
 * Voice input INSIDE the app (android.speech.SpeechRecognizer).
 *
 * The first version opened the phone's own "Speak now" window (RecognizerIntent). That window belongs to
 * the Google app, so on a phone where the Google app is missing or switched off nothing opened and the
 * screen said "Voice input is not available". A SpeechRecognizer talks to the speech ENGINE directly:
 * any installed engine works, the words appear while the person is still speaking, and the mic can move
 * with the voice. It needs the microphone permission (RECORD_AUDIO); the screen asks for it.
 *
 * If the phone's default engine fails (broken, no permission, does not know the language), the other
 * installed engines are tried one after another before an error is reported.
 *
 * Android rule: use a SpeechRecognizer from the main thread only. Call [release] when the screen closes.
 */
class VoiceInput(
    context: Context,
    private val listeningChanged: (Boolean) -> Unit,   // true while the microphone is open
    private val levelChanged: (Float) -> Unit,         // loudness 0..1, for the mic animation
    private val partialText: (String) -> Unit,         // the words so far, while the person still speaks
    private val finalText: (String) -> Unit,           // the finished sentence
    private val failed: (VoiceError) -> Unit
) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var engines: List<ComponentName?> = listOf(null)
    private var engineIndex = 0
    private var recognizer: SpeechRecognizer? = null
    private var languageTag = "en-US"
    private var listening = false
    private var heard = ""                              // the newest partial text

    /** Open the microphone. [language] is a tag like "uz-UZ". Results arrive through the callbacks. */
    fun start(language: String) {
        languageTag = language
        // null = the phone's default engine; after it every installed engine by name
        engines = listOf<ComponentName?>(null) + installedEngines(app)
        engineIndex = 0
        listen()
    }

    /** The mic was tapped again: stop recording and deliver the words heard so far. */
    fun stop() {
        if (!listening) return
        try { recognizer?.stopListening() } catch (e: Exception) { /* answered by the timer below */ }
        // some engines never answer after a stop: do not leave the screen "listening" forever
        handler.postDelayed(giveUp, 2_500L)
    }

    /** Stop without a result (the app went to the background). */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        if (listening) end()
        try { recognizer?.cancel() } catch (e: Exception) { /* nothing to cancel */ }
    }

    /** The screen is closing: free the microphone and the engine. */
    fun release() {
        cancel()
        destroyRecognizer()
    }

    private fun listen() {
        heard = ""
        destroyRecognizer()              // a fresh recognizer each time: a used one can stay stuck after an error
        val made = try {
            val engine = engines[engineIndex]
            if (engine == null) SpeechRecognizer.createSpeechRecognizer(app)
            else SpeechRecognizer.createSpeechRecognizer(app, engine)
        } catch (e: Exception) {
            null
        }
        if (made == null) {
            problem(VoiceError.Failed)
            return
        }
        recognizer = made
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        listening = true
        listeningChanged(true)
        try {
            made.setRecognitionListener(listener)
            made.startListening(intent)
        } catch (e: Exception) {         // e.g. SecurityException from a broken engine
            problem(VoiceError.Failed)
        }
    }

    /** An engine reported a problem: try the next engine if that can help, otherwise tell the screen. */
    private fun problem(error: VoiceError) {
        if (heard.isNotBlank()) {        // it broke after hearing something: use those words
            val words = heard
            end()
            finalText(words)
            return
        }
        val nextEngineMayHelp = error == VoiceError.Failed || error == VoiceError.NoLanguage || error == VoiceError.NoPermission
        if (nextEngineMayHelp && engineIndex < engines.lastIndex) {
            engineIndex += 1
            listening = true
            handler.postDelayed(tryNextEngine, 150L)
            return
        }
        end()
        failed(error)
    }

    private fun end() {
        handler.removeCallbacksAndMessages(null)
        listening = false
        levelChanged(0f)
        listeningChanged(false)
    }

    private fun destroyRecognizer() {
        try { recognizer?.destroy() } catch (e: Exception) { /* already gone */ }
        recognizer = null
    }

    private val tryNextEngine = Runnable { if (listening) listen() }

    private val giveUp = Runnable {
        if (listening) {
            val words = heard
            end()
            try { recognizer?.cancel() } catch (e: Exception) { /* nothing to cancel */ }
            if (words.isBlank()) failed(VoiceError.NothingHeard) else finalText(words)
        }
    }

    private fun words(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        // loudness in decibels, about -2 (silence) .. 10 (loud speech)
        override fun onRmsChanged(rmsdB: Float) {
            if (listening) levelChanged(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = words(partialResults)
            if (listening && text.isNotBlank()) {
                heard = text
                partialText(text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return                       // some phones report the result twice
            val text = words(results).ifBlank { heard }
            end()
            if (text.isBlank()) failed(VoiceError.NothingHeard) else finalText(text)
        }

        override fun onError(error: Int) {
            if (!listening) return                       // e.g. the "client" error some engines send after a stop
            problem(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceError.NothingHeard
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> VoiceError.NoInternet
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceError.NoPermission
                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> VoiceError.NoLanguage
                    else -> VoiceError.Failed
                }
            )
        }
    }

    companion object {
        /** The Google app: its engine knows Uzbek and Russian, and its page is where a missing engine is installed. */
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE exist only since Android 12, so by number
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        /** true = this phone has a speech engine the app can listen with. */
        fun isAvailable(context: Context): Boolean = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }

        /** Every speech engine installed on this phone, the Google app's first. */
        @Suppress("DEPRECATION")
        private fun installedEngines(context: Context): List<ComponentName> = try {
            context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .mapNotNull { it.serviceInfo }
                .map { ComponentName(it.packageName, it.name) }
                .sortedBy { if (it.packageName == GOOGLE_APP) 0 else 1 }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
