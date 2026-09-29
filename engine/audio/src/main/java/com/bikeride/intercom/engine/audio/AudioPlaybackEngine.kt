package com.bikeride.intercom.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-latency audio playback engine utilizing AudioTrack in streaming mode.
 * Includes synthetic emergency horn/siren generator for convoy alerts.
 */
class AudioPlaybackEngine {

    private val _peerAmplitude = MutableStateFlow(0f)
    val peerAmplitude: StateFlow<Float> = _peerAmplitude.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var alertJob: Job? = null

    fun startPlayback() {
        if (audioTrack != null) return

        val minBufSize = AudioTrack.getMinBufferSize(
            AudioConfig.SAMPLE_RATE_HZ,
            AudioConfig.CHANNEL_OUT,
            AudioConfig.ENCODING
        )
        val bufferSize = maxOf(minBufSize, AudioConfig.FRAME_SIZE_BYTES * 4)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioConfig.ENCODING)
                        .setSampleRate(AudioConfig.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioConfig.CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Timber.i("AudioTrack playback initialized and playing")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AudioTrack")
        }
    }

    fun playAudioFrame(frame: ByteArray) {
        val track = audioTrack ?: return
        try {
            track.write(frame, 0, frame.size)

            // Calculate peer amplitude for UI waveform
            val shortBuffer = ShortArray(frame.size / 2)
            ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer)
            var sumSquares = 0.0
            for (s in shortBuffer) {
                sumSquares += s * s
            }
            val rms = sqrt(sumSquares / shortBuffer.size)
            _peerAmplitude.value = (rms / 8000.0).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            Timber.e(e, "Error writing frame to AudioTrack")
        }
    }

    /**
     * Synthesizes and plays a loud, dual-tone emergency convoy siren (e.g. 900 Hz / 1600 Hz)
     * directly into the AudioTrack. Guaranteed to work on all devices without external media assets.
     */
    fun playEmergencyHornAlert(scope: CoroutineScope) {
        if (alertJob?.isActive == true) return
        alertJob = scope.launch(Dispatchers.Default) {
            val track = audioTrack ?: return@launch
            val durationMs = 1200
            val samples = (AudioConfig.SAMPLE_RATE_HZ * durationMs) / 1000
            val sirenBuffer = ByteArray(samples * 2)
            val shortBuffer = ShortArray(samples)

            val freq1 = 880.0  // A5
            val freq2 = 1760.0 // A6

            for (i in 0 until samples) {
                val t = i.toDouble() / AudioConfig.SAMPLE_RATE_HZ
                // Alternate frequency every 150ms
                val currentFreq = if ((i / (AudioConfig.SAMPLE_RATE_HZ * 0.15).toInt()) % 2 == 0) freq1 else freq2
                val angle = 2.0 * Math.PI * currentFreq * t
                val sampleValue = (sin(angle) * 28000).toInt().toShort() // loud amplitude
                shortBuffer[i] = sampleValue
            }

            ByteBuffer.wrap(sirenBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortBuffer)

            try {
                // Play in 640-byte chunks
                var offset = 0
                while (offset < sirenBuffer.size && isActive) {
                    val chunkSize = minOf(AudioConfig.FRAME_SIZE_BYTES, sirenBuffer.size - offset)
                    track.write(sirenBuffer, offset, chunkSize)
                    offset += chunkSize
                    delay(20)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error playing emergency horn")
            }
        }
    }

    fun stopPlayback() {
        alertJob?.cancel()
        alertJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AudioTrack")
        }
        audioTrack = null
        _peerAmplitude.value = 0f
    }
}
