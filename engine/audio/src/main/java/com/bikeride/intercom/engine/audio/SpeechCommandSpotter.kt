package com.bikeride.intercom.engine.audio

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** What the rider said, after confirmation rules. */
enum class SpokenCommand {
    /** First "SOS" heard: ask the rider to say it again. */
    SOS_ARMED,
    /** "SOS" heard twice within [SpeechCommandSpotter.CONFIRM_WINDOW_MS]: raise the alarm. */
    SOS_CONFIRMED
}

sealed interface SpeechModelState {
    data object NotInstalled : SpeechModelState
    data class Downloading(val percent: Int) : SpeechModelState
    data object Ready : SpeechModelState
    data class Failed(val reason: String) : SpeechModelState
}

/**
 * Offline "SOS" voice trigger built on Vosk.
 *
 * It listens to the same 16 kHz frames the intercom already captures (no second microphone,
 * no Google Assistant popups, no internet while riding). Recognition is limited to a tiny word
 * list, which keeps it accurate and light on battery. To avoid false alarms from wind or chatter,
 * "SOS" must be heard twice within a few seconds.
 *
 * The English model (~40 MB) is downloaded once, when the rider enables Voice SOS.
 */
@Singleton
class SpeechCommandSpotter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
    private val modelDir = File(context.filesDir, MODEL_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SpeechModelState>(
        if (File(modelDir, "am").exists()) SpeechModelState.Ready else SpeechModelState.NotInstalled
    )
    val state: StateFlow<SpeechModelState> = _state.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false) && _state.value == SpeechModelState.Ready)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _commands = MutableSharedFlow<SpokenCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<SpokenCommand> = _commands.asSharedFlow()

    @Volatile private var recognizer: Recognizer? = null
    private var model: Model? = null
    private var loading: Job? = null
    private var firstSosAt = 0L

    fun setEnabled(on: Boolean) {
        if (on && _state.value != SpeechModelState.Ready) {
            download()
            return
        }
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) unload()
    }

    /** Downloads and unpacks the model once, then enables Voice SOS. */
    fun download() {
        if (_state.value is SpeechModelState.Downloading) return
        _state.value = SpeechModelState.Downloading(0)
        scope.launch {
            val tmp = File(context.filesDir, "$MODEL_NAME.partial")
            try {
                tmp.deleteRecursively()
                val client = OkHttpClient()
                client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("empty response")
                    val total = body.contentLength().coerceAtLeast(1)
                    var read = 0L
                    val counting = object : java.io.FilterInputStream(body.byteStream()) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = super.read(b, off, len)
                            if (n > 0) {
                                read += n
                                _state.value = SpeechModelState.Downloading((read * 100 / total).toInt().coerceIn(0, 99))
                            }
                            return n
                        }
                    }
                    unzip(counting, tmp)
                }
                // The zip holds one top-level folder named after the model
                val unpacked = File(tmp, MODEL_NAME).takeIf { it.exists() } ?: tmp
                modelDir.deleteRecursively()
                if (!unpacked.renameTo(modelDir)) error("could not move model")
                tmp.deleteRecursively()
                _state.value = SpeechModelState.Ready
                _enabled.value = true
                prefs.edit().putBoolean(KEY_ENABLED, true).apply()
                Timber.i("Voice SOS model installed")
            } catch (e: Exception) {
                Timber.w(e, "Voice SOS model download failed")
                tmp.deleteRecursively()
                _state.value = SpeechModelState.Failed(e.message ?: "download failed")
            }
        }
    }

    private fun unzip(input: java.io.InputStream, target: File) {
        ZipInputStream(input.buffered()).use { zip ->
            val root = target.canonicalPath
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(root)) error("bad zip entry")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Feed one 20 ms frame of 16 kHz mono PCM. Call from a single thread.
     * Cheap no-op while Voice SOS is off or the model is still loading.
     */
    fun feed(frame: ByteArray) {
        if (!_enabled.value) return
        val rec = recognizer
        if (rec == null) {
            ensureLoaded()
            return
        }
        try {
            if (rec.acceptWaveForm(frame, frame.size)) {
                handleText(JSONObject(rec.result).optString("text"))
            }
        } catch (e: Throwable) {
            Timber.w(e, "Speech recognition error")
        }
    }

    private fun handleText(text: String) {
        if (text.isBlank()) return
        val lower = text.lowercase()
        val words = lower.split(" ").toSet()
        val isSos = "s o s" in lower || words.any { it in TRIGGER_WORDS }
        if (!isSos) return
        val now = SystemClock.elapsedRealtime()
        if (now - firstSosAt <= CONFIRM_WINDOW_MS) {
            firstSosAt = 0L
            Timber.w("Voice SOS confirmed")
            _commands.tryEmit(SpokenCommand.SOS_CONFIRMED)
        } else {
            firstSosAt = now
            Timber.i("Voice SOS armed, waiting for confirmation")
            _commands.tryEmit(SpokenCommand.SOS_ARMED)
        }
    }

    private fun ensureLoaded() {
        if (loading?.isActive == true || _state.value != SpeechModelState.Ready) return
        loading = scope.launch {
            try {
                val m = Model(modelDir.absolutePath)
                model = m
                recognizer = Recognizer(m, AudioConfig.SAMPLE_RATE_HZ.toFloat(), GRAMMAR)
                Timber.i("Voice SOS listening")
            } catch (e: Throwable) {
                // Includes UnsatisfiedLinkError: a phone without a compatible native speech library
                // must lose Voice SOS, never the whole app.
                Timber.e(e, "Could not load speech engine")
                _enabled.value = false
                if (e is UnsatisfiedLinkError) {
                    _state.value = SpeechModelState.Failed("not supported on this phone")
                } else {
                    _state.value = SpeechModelState.Failed("model damaged — download again")
                    modelDir.deleteRecursively()
                }
            }
        }
    }

    /** Frees the model's memory (~50 MB) when Voice SOS is switched off. */
    fun unload() {
        loading?.cancel()
        val rec = recognizer
        recognizer = null
        try { rec?.close() } catch (_: Exception) {}
        try { model?.close() } catch (_: Exception) {}
        model = null
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SIZE_MB = 40
        const val CONFIRM_WINDOW_MS = 6_000L
        private const val KEY_ENABLED = "VOICE_SOS_ENABLED"
        private val TRIGGER_WORDS = setOf("sos", "help", "emergency")

        /**
         * Only these words can be recognised; everything else becomes [unk]. The everyday words
         * (hello, helmet, stop…) are decoys: without them "hello" was heard as "help". Tested 19/19
         * against synthesized speech, including trigger and non-trigger phrases.
         */
        private const val GRAMMAR = "[\"sos\", \"s o s\", \"help\", \"emergency\", " +
            "\"hello\", \"helmet\", \"hey\", \"okay\", \"yes\", \"no\", \"stop\", \"slow\", \"go\", \"[unk]\"]"
    }
}
