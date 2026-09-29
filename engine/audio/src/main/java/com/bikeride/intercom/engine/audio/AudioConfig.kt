package com.bikeride.intercom.engine.audio

import android.media.AudioFormat

/**
 * Audio hardware configuration optimized for wideband motorcycle intercom speech.
 * 16 kHz sample rate provides crystal-clear voice clarity (mSBC standard)
 * while consuming minimal RF bandwidth (~32 KB/s uncompressed, highly responsive).
 */
object AudioConfig {
    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // 20ms frames at 16 kHz = 320 samples = 640 bytes
    const val FRAME_DURATION_MS = 20
    const val SAMPLES_PER_FRAME = SAMPLE_RATE_HZ * FRAME_DURATION_MS / 1000 // 320
    const val BYTES_PER_SAMPLE = 2 // 16-bit PCM
    const val FRAME_SIZE_BYTES = SAMPLES_PER_FRAME * BYTES_PER_SAMPLE // 640
}
