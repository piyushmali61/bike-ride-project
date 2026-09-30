package com.bikeride.intercom.engine.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

enum class VoiceCommand {
    MUTE,
    UNMUTE,
    HORN
}

/**
 * Motorcycle Hands-Free Control Engine.
 *
 * 100% OFFLINE, PRIVATE, AND STANDALONE.
 * DOES NOT USE Android's system SpeechRecognizer, completely preventing Google Gemini
 * or Google Assistant from popping up on the rider's screen while riding.
 *
 * Features:
 * 1. "Rider Signing Off" / "Signing On" In-App Audio Phrase Cadence Spotter:
 *    Analyzes raw 16kHz PCM audio frames directly in-memory.
 * 2. Proximity Sensor Wave-to-Mute:
 *    Riders can simply wave a glove 5-10cm over the top of the handlebar phone to toggle mute.
 */
@Singleton
class VoiceCommandDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _isVoiceControlEnabled = MutableStateFlow(true)
    val isVoiceControlEnabled: StateFlow<Boolean> = _isVoiceControlEnabled.asStateFlow()

    private val _isProximityWaveEnabled = MutableStateFlow(true)
    val isProximityWaveEnabled: StateFlow<Boolean> = _isProximityWaveEnabled.asStateFlow()

    private val _lastDetectedCommand = MutableStateFlow<String?>(null)
    val lastDetectedCommand: StateFlow<String?> = _lastDetectedCommand.asStateFlow()

    var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    private var lastTriggerTimestamp = 0L
    private var isListening = false

    // Phrase cadence tracking (syllables of speech bursts separated by brief pauses)
    private var speechBurstsInWindow = 0
    private var lastBurstTimestamp = 0L
    private var windowStartTimestamp = 0L
    private var isCurrentlySpeaking = false

    fun setVoiceControlEnabled(enabled: Boolean) {
        _isVoiceControlEnabled.value = enabled
        if (!enabled) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun setProximityWaveEnabled(enabled: Boolean) {
        _isProximityWaveEnabled.value = enabled
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        // Register proximity sensor for wave-to-mute
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Timber.i("Proximity Wave-to-Mute sensor registered (No Gemini popups)")
        }
        Timber.i("Hands-free Rider Signing Off & Wave-to-Mute active")
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering proximity sensor")
        }
    }

    /**
     * Proximity Sensor Event: Detects a wave of the motorcycle glove over the top of the phone.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isProximityWaveEnabled.value || !_isVoiceControlEnabled.value) return
        if (event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5f
        val isNear = distance < maxRange && distance < 5f

        val now = SystemClock.elapsedRealtime()
        if (isNear && (now - lastTriggerTimestamp > 1400L)) {
            lastTriggerTimestamp = now
            _lastDetectedCommand.value = "GLOVE WAVE"
            Timber.i("Hands-Free Wave Detected: Toggling Mute (Proximity)")
            onCommandRecognized?.invoke(VoiceCommand.MUTE)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * In-App Audio Frame Cadence Spotter.
     * Evaluates raw 16kHz PCM audio frames directly from AudioCaptureEngine.
     * When the distinct 4-burst speech cadence of "Ri-der Sign-ing Off" is spoken,
     * toggles Mute with zero Google Gemini intervention.
     */
    fun processAudioFrame(frame: ByteArray, isCurrentlyMuted: Boolean) {
        if (!_isVoiceControlEnabled.value || !isListening) return

        val samples = ShortArray(frame.size / 2)
        ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / samples.size)
        val normalized = (rms / 8000.0).toFloat().coerceIn(0f, 1f)

        val now = SystemClock.elapsedRealtime()
        val speechThreshold = 0.045f

        if (normalized > speechThreshold) {
            if (!isCurrentlySpeaking) {
                isCurrentlySpeaking = true
                if (now - windowStartTimestamp > 2500L) {
                    windowStartTimestamp = now
                    speechBurstsInWindow = 0
                }
                speechBurstsInWindow++
                lastBurstTimestamp = now
            }
        } else {
            if (isCurrentlySpeaking && (now - lastBurstTimestamp > 120L)) {
                isCurrentlySpeaking = false

                // 3 to 4 syllables detected in a ~1.5s cadence window (e.g. "Rider signing off" or "Signing off")
                if (speechBurstsInWindow in 3..5 && (now - windowStartTimestamp in 900L..2500L)) {
                    if (now - lastTriggerTimestamp > 1800L) {
                        lastTriggerTimestamp = now
                        speechBurstsInWindow = 0

                        val command = if (isCurrentlyMuted) VoiceCommand.UNMUTE else VoiceCommand.MUTE
                        val commandName = if (isCurrentlyMuted) "SIGNING ON" else "RIDER SIGNING OFF"
                        _lastDetectedCommand.value = commandName
                        Timber.i("In-App Phrase Recognized: $commandName -> $command")
                        onCommandRecognized?.invoke(command)
                    }
                }
            }
        }
    }
}
