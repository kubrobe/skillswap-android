package uz.skillswap.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Voice input that works on EVERY phone: Vosk, an open-source speech recognizer that runs inside the app.
 *
 * Phones without Google's speech service (no Google app, Huawei, many Chinese phones) cannot use
 * [VoiceInput] or the "Speak now" window. Vosk needs neither: the first time, the app downloads a small
 * voice pack for the chosen language (about 40-50 MB, kept on the phone), then recognizes speech offline.
 *
 * Callbacks arrive on the main thread. Call [release] when the screen closes.
 */
class OfflineVoice(
    context: Context,
    private val listeningChanged: (Boolean) -> Unit,
    private val partialText: (String) -> Unit,
    private val finalText: (String) -> Unit,
    private val failed: (VoiceError) -> Unit
) {
    private val app = context.applicationContext
    private var service: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var listening = false
    private var heard = ""

    /** Opens the microphone. The voice pack for [lang] must be installed ([isInstalled] / [install]). */
    suspend fun start(lang: String) {
        release()
        val model = try {
            loadModel(app, lang)                          // 1-2 s the first time, then cached
        } catch (e: Exception) {
            failed(VoiceError.Failed)
            return
        }
        try {
            val r = Recognizer(model, SAMPLE_RATE)
            recognizer = r
            val s = SpeechService(r, SAMPLE_RATE)        // opens the microphone (needs RECORD_AUDIO)
            service = s
            heard = ""
            listening = true
            listeningChanged(true)
            s.startListening(listener, 15_000)           // stops by itself after 15 s
        } catch (e: Exception) {
            finish()
            failed(VoiceError.Failed)
        }
    }

    /** Second tap on the mic: stop and use what was said. */
    fun stop() {
        if (!listening) return
        try { service?.stop() } catch (e: Exception) { deliver() }   // answered by onFinalResult
    }

    /** Stop without a result, free the microphone. */
    fun release() {
        if (listening) listeningChanged(false)
        listening = false
        closeAll()
    }

    private fun finish() {
        if (listening) listeningChanged(false)
        listening = false
        closeAll()
    }

    /**
     * CRASH FIX: the order matters. Vosk listens on its own thread, which keeps feeding sound to the
     * recognizer. The first version freed the recognizer (native memory) while that thread was still
     * running, so the next sound it fed crashed the whole app right after the words appeared.
     * Now: 1) cancel() stops that thread and WAITS for it, 2) free the microphone, 3) free the recognizer.
     */
    private fun closeAll() {
        val s = service
        val r = recognizer
        service = null
        recognizer = null
        try { s?.cancel() } catch (e: Exception) { }
        try { s?.shutdown() } catch (e: Exception) { }
        try { r?.close() } catch (e: Exception) { }
    }

    /** The words are final: hand them to the screen once. */
    private fun deliver(text: String = heard) {
        if (!listening) return
        finish()
        if (text.isBlank()) failed(VoiceError.NothingHeard) else finalText(text)
    }

    private fun field(json: String?, name: String): String =
        try { JSONObject(json ?: "").optString(name, "").trim() } catch (e: Exception) { "" }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val t = field(hypothesis, "partial")
            if (listening && t.isNotBlank()) {
                heard = t
                partialText(t)
            }
        }

        // Vosk heard a pause after a sentence: that sentence is the answer
        override fun onResult(hypothesis: String?) {
            val t = field(hypothesis, "text")
            if (t.isNotBlank()) deliver(t)
        }

        override fun onFinalResult(hypothesis: String?) {
            deliver(field(hypothesis, "text").ifBlank { heard })
        }

        override fun onError(exception: Exception?) {
            if (!listening) return
            if (heard.isNotBlank()) deliver() else { finish(); failed(VoiceError.Failed) }
        }

        override fun onTimeout() = deliver()
    }

    companion object {
        private const val SAMPLE_RATE = 16_000f

        /** The voice packs (small models, Apache 2.0) from alphacephei.com, the makers of Vosk. */
        private val PACKS = mapOf(
            "en" to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "ru" to "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
            "uz" to "https://alphacephei.com/vosk/models/vosk-model-small-uz-0.22.zip"
        )

        /** "uz-UZ" -> "uz", "ru-RU" -> "ru", anything else -> "en". */
        fun langFor(speechLocale: String): String = speechLocale.substringBefore('-').let { if (it in PACKS) it else "en" }

        private fun dir(context: Context, lang: String) = File(context.filesDir, "vosk/$lang")

        /** true = the voice pack is on the phone (a finished unpack leaves a "ready" mark). */
        fun isInstalled(context: Context, lang: String) = File(dir(context, lang), ".ready").exists()

        /** Downloads and unpacks the voice pack. [progress] gets 0..100. Throws IOException on failure. */
        suspend fun install(context: Context, lang: String, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
            val url = PACKS[lang] ?: throw IOException("no voice pack for $lang")
            val target = dir(context, lang)
            val temp = File(context.filesDir, "vosk/$lang.part")
            temp.deleteRecursively()
            target.deleteRecursively()
            temp.mkdirs()
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: 50_000_000L
                var read = 0L
                var lastShown = -1
                val counting = object : java.io.FilterInputStream(conn.inputStream.buffered(64 * 1024)) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) {
                            read += n
                            val p = (read * 100 / total).toInt().coerceIn(0, 99)
                            if (p != lastShown) {
                                lastShown = p
                                main.post { progress(p) }
                            }
                        }
                        return n
                    }
                }
                val root = temp.canonicalPath + File.separator
                ZipInputStream(counting).use { zip ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val entry = zip.nextEntry ?: break
                        // "vosk-model-small-en-us-0.15/am/final.mdl" -> "am/final.mdl"
                        val name = entry.name.substringAfter('/', "")
                        if (name.isEmpty()) continue
                        val out = File(temp, name)
                        if (!out.canonicalPath.startsWith(root)) continue     // never write outside the folder
                        if (entry.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            File(temp, ".ready").createNewFile()
            if (!temp.renameTo(target)) throw IOException("could not save the voice pack")
            withContext(Dispatchers.Main) { progress(100) }
        }

        // One model in memory at a time (they are big); switching language loads the other one.
        private var cachedLang: String? = null
        private var cachedModel: Model? = null

        private suspend fun loadModel(context: Context, lang: String): Model = withContext(Dispatchers.IO) {
            synchronized(this@Companion) {
                cachedModel?.takeIf { cachedLang == lang }?.let { return@withContext it }
                try { cachedModel?.close() } catch (e: Exception) { }
                cachedModel = null
                val m = Model(dir(context, lang).absolutePath)
                cachedModel = m
                cachedLang = lang
                m
            }
        }
    }
}
