package com.bikeride.intercom.engine.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level audio engine combining real-time capture and playback.
 */
@Singleton
class AudioEngine @Inject constructor(
    private val emergencyHornPlayer: EmergencyHornPlayer,
    private val muteFeedbackManager: MuteFeedbackManager
) {

    val capture = AudioCaptureEngine()
    val playback = AudioPlaybackEngine()

    val micAmplitude: StateFlow<Float> = capture.micAmplitude
    val peerAmplitude: StateFlow<Float> = playback.peerAmplitude
    val isMuted: StateFlow<Boolean> = capture.isMuted
    val outgoingFrames: SharedFlow<ByteArray> = capture.outgoingFrames
    val rawFrames: SharedFlow<ByteArray> = capture.rawFrames

    fun start(scope: CoroutineScope) {
        playback.startPlayback()
        capture.startCapture(scope)
    }

    fun stop() {
        capture.stopCapture()
        playback.stopPlayback()
    }

    fun setMuted(muted: Boolean, scope: CoroutineScope? = null) {
        capture.setMuted(muted)
        muteFeedbackManager.playMuteFeedback(muted)
    }

    fun setVolumeBoost(multiplier: Float) {
        capture.setVolumeBoost(multiplier)
    }

    fun playIncomingFrame(frame: ByteArray) {
        playback.playAudioFrame(frame)
    }

    fun playEmergencyHorn(scope: CoroutineScope? = null) {
        emergencyHornPlayer.playEmergencyAlert()
    }

    fun playMuteChime(muted: Boolean, scope: CoroutineScope? = null) {
        muteFeedbackManager.playMuteFeedback(muted)
    }
}
