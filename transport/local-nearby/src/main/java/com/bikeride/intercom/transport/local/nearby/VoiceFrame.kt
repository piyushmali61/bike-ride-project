package com.bikeride.intercom.transport.local.nearby

import java.nio.ByteBuffer

/**
 * Voice mesh frame (follows the 1-byte PKT_AUDIO type on the wire):
 *
 * ```
 * senderId:4  seq:2  ttl:1  pcm:N
 * ```
 *
 * - `senderId` + `seq` identify a frame, so the same frame arriving over Nearby *and* Hotspot,
 *   or relayed by two riders, is played only once.
 * - `ttl` lets riders in the middle pass voice on: A → B → C even when A and C are out of range.
 */
data class VoiceFrame(val senderId: Int, val seq: Int, val ttl: Int, val pcm: ByteArray) {

    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_SIZE + pcm.size)
        .putInt(senderId)
        .putShort(seq.toShort())
        .put(ttl.toByte())
        .put(pcm)
        .array()

    override fun equals(other: Any?) =
        other is VoiceFrame && senderId == other.senderId && seq == other.seq && ttl == other.ttl && pcm.contentEquals(other.pcm)

    override fun hashCode() = 31 * (31 * senderId + seq) + pcm.contentHashCode()

    companion object {
        const val HEADER_SIZE = 7
        const val DEFAULT_TTL = 4

        /** Frames from older app versions carry bare PCM; they are played but never relayed. */
        fun decode(bytes: ByteArray, legacySenderId: Int, pcmSize: Int): VoiceFrame? = when (bytes.size) {
            pcmSize -> VoiceFrame(legacySenderId, -1, 1, bytes)
            HEADER_SIZE + pcmSize -> {
                val buf = ByteBuffer.wrap(bytes)
                VoiceFrame(
                    senderId = buf.int,
                    seq = buf.short.toInt() and 0xFFFF,
                    ttl = buf.get().toInt() and 0xFF,
                    pcm = bytes.copyOfRange(HEADER_SIZE, bytes.size)
                )
            }
            else -> null
        }
    }
}

/**
 * Remembers recently heard (sender, seq) pairs. 16-bit sequence numbers wrap every ~22 minutes
 * of speech at 50 frames/s, so only a sliding window of recent numbers is kept per sender.
 */
class VoiceDedup(private val window: Int = 512) {
    private val recent = HashMap<Int, LinkedHashSet<Int>>()

    /** True the first time a frame is seen. Legacy frames (seq -1) are always new. */
    @Synchronized
    fun firstTime(frame: VoiceFrame): Boolean {
        if (frame.seq < 0) return true
        val seen = recent.getOrPut(frame.senderId) { LinkedHashSet() }
        if (!seen.add(frame.seq)) return false
        if (seen.size > window) seen.remove(seen.first())
        return true
    }

    @Synchronized
    fun clear() = recent.clear()
}
