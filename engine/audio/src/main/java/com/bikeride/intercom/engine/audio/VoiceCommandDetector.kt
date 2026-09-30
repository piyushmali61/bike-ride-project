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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

enum class VoiceCommand {
    MUTE,
    UNMUTE,
    HORN
}

/**
 * Motorcycle Hands-Free Control Engine.
 *
 * 100% OFFLINE, PRIVATE, AND TOUCH-FREE.
 * Allows riders wearing thick riding gloves to mute/unmute audio without touching the phone screen:
 * 1. Glove Wave over phone top (< 6cm proximity wave).
 * 2. Mount / Handlebar Double-Tap (quick double tap on the phone mount/handlebar).
 *
 * Safe: Speech NEVER automatically cuts audio (prevents accidental mute while talking).
 * Completely avoids Android SpeechRecognizer to eliminate Google Assistant/Gemini popups.
 */
@Singleton
class VoiceCommandDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _isHandsFreeControlEnabled = MutableStateFlow(true)
    val isVoiceControlEnabled: StateFlow<Boolean> = _isHandsFreeControlEnabled.asStateFlow()

    private val _lastDetectedCommand = MutableStateFlow<String?>("READY (WAVE OR DOUBLE-TAP)")
    val lastDetectedCommand: StateFlow<String?> = _lastDetectedCommand.asStateFlow()

    var onCommandRecognized: ((VoiceCommand) -> Unit)? = null

    private var lastTriggerTimestamp = 0L
    private var lastTapTimestamp = 0L
    private var tapSpikeCount = 0
    private var isListening = false

    fun setVoiceControlEnabled(enabled: Boolean) {
        _isHandsFreeControlEnabled.value = enabled
        if (!enabled) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Timber.i("Proximity Wave-to-Mute sensor registered")
        }

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Timber.i("Double-Tap Mount sensor registered")
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering sensors")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isHandsFreeControlEnabled.value) return

        val now = SystemClock.elapsedRealtime()

        // 1. Proximity Sensor: Glove Wave 5-10cm over top of phone
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = distance < maxRange && distance < 6f

            if (isNear && (now - lastTriggerTimestamp > 1800L)) {
                lastTriggerTimestamp = now
                _lastDetectedCommand.value = "GLOVE WAVE MUTE"
                Timber.i("Hands-Free Glove Wave Detected: Toggling Mute")
                onCommandRecognized?.invoke(VoiceCommand.MUTE)
            }
        }

        // 2. Accelerometer: Handlebar / Mount Double-Tap
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gTotal = sqrt(x * x + y * y + z * z)
            val deltaG = abs(gTotal - SensorManager.GRAVITY_EARTH)

            // Detect sharp vibration spike (> 14 m/s² delta)
            if (deltaG > 14.0f) {
                val interval = now - lastTapTimestamp
                if (interval in 100..450) {
                    tapSpikeCount++
                    if (tapSpikeCount >= 2 && (now - lastTriggerTimestamp > 1800L)) {
                        lastTriggerTimestamp = now
                        tapSpikeCount = 0
                        _lastDetectedCommand.value = "DOUBLE-TAP MUTE"
                        Timber.i("Hands-Free Mount Double-Tap Detected: Toggling Mute")
                        onCommandRecognized?.invoke(VoiceCommand.MUTE)
                    }
                } else if (interval > 450) {
                    tapSpikeCount = 1
                }
                lastTapTimestamp = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun processAudioFrame(frame: ByteArray, isCurrentlyMuted: Boolean) {
        // Disabled: Rider speech must NEVER automatically mute audio while talking
    }
}
