package com.bikeride.intercom.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Low-latency multi-rider playback.
 *
 * Network threads only drop frames into a small per-rider jitter buffer ([playAudioFrame] never
 * blocks). One dedicated thread owns the AudioTrack: every 20 ms it takes one frame from each
 * rider, mixes them, and writes the result. Because only that thread touches the AudioTrack, and
 * [stopPlayback] waits for it to exit before releasing, the track is never used after release.
 */
class AudioPlaybackEngine {

    private val _peerAmplitude = MutableStateFlow(0f)
    val peerAmplitude: StateFlow<Float> = _peerAmplitude.asStateFlow()

    private class RiderBuffer {
        val frames = ArrayDeque<ShortArray>()
        var primed = false
        var lastFrameAt = 0L
    }

    private val buffers = HashMap<Int, RiderBuffer>()
    private val lock = Object()

    /** Each playback thread has its own flag, so a quick stop/start can never revive an old thread. */
    @Volatile private var active: AtomicBoolean? = null
    private var thread: Thread? = null

    private val running: Boolean get() = active?.get() == true

    @Synchronized
    fun startPlayback() {
        if (running) return
        val flag = AtomicBoolean(true)
        active = flag
        thread = Thread({ playbackLoop(flag) }, "AstraRide-Playback").apply { start() }
    }

    /** Queue 20 ms of PCM from one rider. Safe from any thread; drops the oldest audio if a rider floods us. */
    fun playAudioFrame(senderId: Int, frame: ByteArray) {
        if (!running || frame.size != AudioConfig.FRAME_SIZE_BYTES) return
        val samples = ShortArray(AudioConfig.SAMPLES_PER_FRAME)
        for (i in samples.indices) {
            samples[i] = ((frame[2 * i + 1].toInt() shl 8) or (frame[2 * i].toInt() and 0xFF)).toShort()
        }
        synchronized(lock) {
            val buf = buffers.getOrPut(senderId) { RiderBuffer() }
            buf.frames.addLast(samples)
            buf.lastFrameAt = SystemClock.elapsedRealtime()
            while (buf.frames.size > MAX_BUFFERED_FRAMES) buf.frames.removeFirst()
        }
    }

    private fun playbackLoop(flag: AtomicBoolean) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = createTrack() ?: run {
            flag.set(false)
            return
        }
        val mix = IntArray(AudioConfig.SAMPLES_PER_FRAME)
        val out = ShortArray(AudioConfig.SAMPLES_PER_FRAME)
        try {
            track.play()
            while (flag.get()) {
                mix.fill(0)
                var riders = 0
                synchronized(lock) {
                    val now = SystemClock.elapsedRealtime()
                    val it = buffers.entries.iterator()
                    while (it.hasNext()) {
                        val buf = it.next().value
                        if (buf.frames.isEmpty() && now - buf.lastFrameAt > RIDER_IDLE_MS) {
                            it.remove()
                            continue
                        }
                        // Wait for a small cushion before starting a rider, to absorb network jitter
                        if (!buf.primed && buf.frames.size >= PREBUFFER_FRAMES) buf.primed = true
                        if (!buf.primed) continue
                        val frame = buf.frames.removeFirstOrNull()
                        if (frame == null) {
                            buf.primed = false // underrun: rebuild the cushion
                            continue
                        }
                        for (i in mix.indices) mix[i] += frame[i].toInt()
                        riders++
                    }
                }

                var sumSquares = 0.0
                for (i in out.indices) {
                    val s = mix[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out[i] = s.toShort()
                    sumSquares += s.toDouble() * s
                }
                _peerAmplitude.value = if (riders == 0) 0f
                else (sqrt(sumSquares / out.size) / 8000.0).toFloat().coerceIn(0f, 1f)

                // Blocking write paces this loop at real time (silence when nobody talks)
                if (track.write(out, 0, out.size) < 0) break
            }
        } catch (e: Exception) {
            Timber.e(e, "Playback loop error")
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
            _peerAmplitude.value = 0f
        }
    }

    private fun createTrack(): AudioTrack? = try {
        val minBuf = AudioTrack.getMinBufferSize(AudioConfig.SAMPLE_RATE_HZ, AudioConfig.CHANNEL_OUT, AudioConfig.ENCODING)
        AudioTrack.Builder()
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
            .setBufferSizeInBytes(maxOf(minBuf, AudioConfig.FRAME_SIZE_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    } catch (e: Exception) {
        Timber.e(e, "Failed to initialize AudioTrack")
        null
    }

    fun playEmergencyHornAlert(scope: CoroutineScope) {
        // Handled with dedicated hardware USAGE_ALARM stream by EmergencyHornPlayer
        Timber.d("Emergency horn playback delegated to EmergencyHornPlayer")
    }

    fun playMuteChime(isMuted: Boolean, scope: CoroutineScope) {
        // Handled by MuteFeedbackManager
        Timber.d("Mute chime handled via dedicated feedback channel (isMuted=$isMuted)")
    }

    @Synchronized
    fun stopPlayback() {
        active?.set(false)
        active = null
        thread?.let {
            it.join(1000)
            if (it.isAlive) Timber.w("Playback thread did not stop in time")
        }
        thread = null
        synchronized(lock) { buffers.clear() }
    }

    companion object {
        /** 40 ms cushion before a rider starts playing. */
        private const val PREBUFFER_FRAMES = 2
        /** Never hold more than 200 ms per rider: late audio is dropped, latency cannot creep up. */
        private const val MAX_BUFFERED_FRAMES = 10
        private const val RIDER_IDLE_MS = 3000L
    }
}
