package com.bikeride.intercom.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-decibel, hardware-backed Emergency Horn Alert Player.
 *
 * Guarantees an immediate, loud convoy alarm:
 * 1. Dual-tone synthesized siren (880Hz / 1320Hz) via AudioTrack on USAGE_ALARM (loudspeaker).
 * 2. Simultaneous ToneGenerator on STREAM_ALARM (100% volume hardware buzzer).
 * 3. Haptic SOS vibration pulses for helmet / handlebar tactile alerting.
 * 4. Completely decoupled from active intercom audio streaming so it sounds reliably
 *    under all states (idle, connecting, riding, muted).
 */
@Singleton
class EmergencyHornPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastTriggerTime = 0L

    // Precomputed 1.5s dual-tone siren PCM buffer (44.1kHz 16-bit mono)
    private val sirenPcmData: ByteArray by lazy {
        generateSirenPcm()
    }

    private fun generateSirenPcm(): ByteArray {
        val sampleRate = 44100
        val durationSeconds = 1.5
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val pcm = ByteArray(totalSamples * 2)

        val freq1 = 880.0   // A5
        val freq2 = 1320.0  // E6 (piercing emergency fifth)
        val intervalSamples = (sampleRate * 0.22).toInt() // alternate every 220ms

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val freq = if ((i / intervalSamples) % 2 == 0) freq1 else freq2
            phase += (2.0 * PI * freq) / sampleRate
            // 31,000 max amplitude for maximum loud acoustic cut-through
            val sample = (sin(phase) * 31000.0).toInt().coerceIn(-32767, 32767).toShort()
            val byteIdx = i * 2
            pcm[byteIdx] = (sample.toInt() and 0xFF).toByte()
            pcm[byteIdx + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    fun playEmergencyAlert() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < 1800L) {
            Timber.d("Emergency horn alert debounced (${now - lastTriggerTime}ms)")
            return
        }
        lastTriggerTime = now

        Timber.w("🚨 SOUNDING EMERGENCY HORN ALERT 🚨")

        // 1. Trigger tactile haptic vibration
        triggerVibration()

        // 2. Play AudioTrack siren & ToneGenerator concurrently
        scope.launch {
            playAudioTrackSiren()
        }
        scope.launch {
            playToneGenerator()
        }
    }

    private fun triggerVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 350, 100, 350, 100, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Timber.w(e, "Haptic vibration failed")
        }
    }

    private fun playAudioTrackSiren() {
        var track: AudioTrack? = null
        try {
            val pcm = sirenPcmData
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setVolume(1.0f)
            track.play()

            // Wait for duration then release
            Thread.sleep(1600)
        } catch (e: Exception) {
            Timber.e(e, "AudioTrack alarm playback error, relying on ToneGenerator")
            tryFallbackMediaTrack()
        } finally {
            try {
                if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun tryFallbackMediaTrack() {
        try {
            val pcm = sirenPcmData
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setVolume(1.0f)
            track.play()
            Thread.sleep(1600)
            track.stop()
            track.release()
        } catch (e: Exception) {
            Timber.e(e, "Fallback media AudioTrack failed")
        }
    }

    private fun playToneGenerator() {
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
            Thread.sleep(1600)
        } catch (e: Exception) {
            Timber.w(e, "ToneGenerator STREAM_ALARM failed, trying STREAM_MUSIC")
            try {
                toneGen?.release()
                toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
                Thread.sleep(1600)
            } catch (t: Exception) {
                Timber.e(t, "ToneGenerator STREAM_MUSIC failed")
            }
        } finally {
            try {
                toneGen?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
