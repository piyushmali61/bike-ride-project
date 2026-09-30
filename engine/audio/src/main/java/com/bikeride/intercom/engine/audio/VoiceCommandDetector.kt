package com.bikeride.intercom.engine.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceCommand {
    MUTE,
    UNMUTE,
    HORN
}

/**
 * Battery-conscious, hands-free voice command recognizer for motorcycle riders.
 * Enables bikers wearing gloves and riding at speed to simply speak "Mute" or "Unmute"
 * to toggle their helmet audio without taking their hands off the handlebars.
 */
@Singleton
class VoiceCommandDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastCommandTimestamp = 0L

    private val _isVoiceControlEnabled = MutableStateFlow(true)
    val isVoiceControlEnabled: StateFlow<Boolean> = _isVoiceControlEnabled.asStateFlow()

    private val _lastDetectedCommand = MutableStateFlow<String?>(null)
    val lastDetectedCommand: StateFlow<String?> = _lastDetectedCommand.asStateFlow()

    var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    private val recognizerIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    fun setVoiceControlEnabled(enabled: Boolean) {
        _isVoiceControlEnabled.value = enabled
        if (!enabled) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (!_isVoiceControlEnabled.value) return
        mainHandler.post {
            if (isListening) return@post
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Timber.w("Speech recognition is not available on this device")
                    return@post
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                }

                speechRecognizer?.startListening(recognizerIntent)
                isListening = true
                Timber.i("Hands-free VoiceCommandDetector listening for 'MUTE' / 'UNMUTE'")
            } catch (e: Exception) {
                Timber.e(e, "Error starting SpeechRecognizer")
                scheduleRestart(1500)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isListening = false
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Timber.e(e, "Error stopping SpeechRecognizer")
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                // Transient errors during riding (silence, no match) are expected; restart seamlessly
                Timber.d("SpeechRecognizer error code: $error")
                mainHandler.post {
                    isListening = false
                    if (_isVoiceControlEnabled.value) {
                        scheduleRestart(800)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                processBundle(results)
                mainHandler.post {
                    isListening = false
                    if (_isVoiceControlEnabled.value) {
                        scheduleRestart(300)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processBundle(partialResults)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processBundle(bundle: Bundle?) {
        if (bundle == null) return
        val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT).trim()
            if (checkAndDispatchKeyword(lower)) {
                break
            }
        }
    }

    private fun checkAndDispatchKeyword(text: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        // 1.2 second debounce to prevent rapid duplicate triggers
        if (now - lastCommandTimestamp < 1200L) return false

        return when {
            text.contains("unmute") || text.contains("un mute") || text.contains("mic on") || text.contains("turn on mic") -> {
                lastCommandTimestamp = now
                _lastDetectedCommand.value = "UNMUTE"
                Timber.i("Voice Command Recognized: UNMUTE ('$text')")
                onCommandRecognized?.invoke(VoiceCommand.UNMUTE)
                true
            }
            text.contains("mute") || text.contains("mic off") || text.contains("turn off mic") || text.contains("silent") -> {
                lastCommandTimestamp = now
                _lastDetectedCommand.value = "MUTE"
                Timber.i("Voice Command Recognized: MUTE ('$text')")
                onCommandRecognized?.invoke(VoiceCommand.MUTE)
                true
            }
            text.contains("horn") || text.contains("siren") || text.contains("alert") -> {
                lastCommandTimestamp = now
                _lastDetectedCommand.value = "HORN"
                Timber.i("Voice Command Recognized: HORN ('$text')")
                onCommandRecognized?.invoke(VoiceCommand.HORN)
                true
            }
            else -> false
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (_isVoiceControlEnabled.value) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(recognizerIntent)
                    isListening = true
                } catch (e: Exception) {
                    Timber.d(e, "Retrying speech start")
                }
            }
        }, delayMs)
    }
}
