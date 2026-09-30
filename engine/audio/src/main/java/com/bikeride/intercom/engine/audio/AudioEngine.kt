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
class AudioEngine @Inject constructor() {

    val capture = AudioCaptureEngine()
    val playback = AudioPlaybackEngine()

    val micAmplitude: StateFlow<Float> = capture.micAmplitude
    val peerAmplitude: StateFlow<Float> = playback.peerAmplitude
    val isMuted: StateFlow<Boolean> = capture.isMuted
    val outgoingFrames: SharedFlow<ByteArray> = capture.outgoingFrames

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
        scope?.let {
            playback.playMuteChime(muted, it)
        }
    }

    fun setVolumeBoost(multiplier: Float) {
        capture.setVolumeBoost(multiplier)
    }

    fun playIncomingFrame(frame: ByteArray) {
        playback.playAudioFrame(frame)
    }

    fun playEmergencyHorn(scope: CoroutineScope) {
        playback.playEmergencyHornAlert(scope)
    }

    fun playMuteChime(muted: Boolean, scope: CoroutineScope) {
        playback.playMuteChime(muted, scope)
    }
}
