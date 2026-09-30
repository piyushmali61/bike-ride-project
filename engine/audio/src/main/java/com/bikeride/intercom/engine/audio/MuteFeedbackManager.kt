package com.bikeride.intercom.engine.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe, zero-latency Mute/Unmute audio and haptic feedback engine.
 *
 * CRITICAL STABILITY GUARANTEE:
 * Does NOT write into the streaming voice AudioTrack, completely preventing
 * the native SIGSEGV crash in libaudioclient.so when mute is triggered while
 * incoming audio frames are playing.
 */
@Singleton
class MuteFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun playMuteFeedback(isMuted: Boolean) {
        scope.launch {
            // 1. Tactile haptic feedback
            triggerVibration(isMuted)

            // 2. Hardware ToneGenerator audio cue (safe, decoupled from AudioTrack)
            playTone(isMuted)
        }
    }

    private fun triggerVibration(isMuted: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            // Muted: 2 short tactile pulses; Unmuted: 1 crisp pulse
            val pattern = if (isMuted) {
                longArrayOf(0, 70, 60, 70)
            } else {
                longArrayOf(0, 100)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Timber.w(e, "Mute feedback vibration failed")
        }
    }

    private fun playTone(isMuted: Boolean) {
        var toneGen: ToneGenerator? = null
        try {
            // Use STREAM_MUSIC or STREAM_NOTIFICATION for clear helmet/speaker earcon
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            val toneType = if (isMuted) {
                ToneGenerator.TONE_PROP_BEEP2 // Descending double pip for Mute
            } else {
                ToneGenerator.TONE_PROP_BEEP  // Ascending single pip for Unmute
            }
            toneGen.startTone(toneType, 120)
            Thread.sleep(140)
        } catch (e: Exception) {
            Timber.d(e, "ToneGenerator mute feedback skipped")
        } finally {
            try {
                toneGen?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
