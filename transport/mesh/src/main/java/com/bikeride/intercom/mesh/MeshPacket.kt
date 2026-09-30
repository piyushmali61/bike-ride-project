package com.bikeride.intercom.mesh

import java.nio.ByteBuffer

/** What a mesh packet carries. Codes are part of the wire format — never renumber. */
enum class MeshType(val code: Byte) {
    CHAT(1), SOS(2), LOCATION(3), PROFILE(4), ACK(5),

    /** One piece of a photo; see [ImageChunk]. */
    IMAGE(6),

    /** Smart Arrival / Destination Reached event. */
    DESTINATION(7);

    companion object {
        fun fromCode(code: Byte): MeshType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * ConvoyMesh wire packet (v1). Fixed 42-byte big-endian header followed by the payload:
 *
 * ```
 * version:1 type:1 ttl:1 flags:1 timestamp:8 messageId:8 senderId:8 recipientId:8 roomTag:4 length:2 payload:N
 * ```
 *
 * - `ttl` is the only field relays change, so it is excluded from [aad].
 * - `recipientId == BROADCAST` means "everyone in the room".
 * - `roomTag` lets relays forward packets for rooms they cannot decrypt.
 */
data class MeshPacket(
    val type: MeshType,
    val ttl: Int,
    val flags: Int = 0,
    val timestamp: Long,
    val messageId: Long,
    val senderId: Long,
    val recipientId: Long = BROADCAST,
    val roomTag: Int,
    val payload: ByteArray
) {
    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_SIZE + payload.size).apply {
        writeHeader(this, ttl)
        put(payload)
    }.array()

    /** Authenticated header bytes: everything except the mutable TTL. */
    fun aad(): ByteArray = ByteBuffer.allocate(HEADER_SIZE).apply { writeHeader(this, 0) }.array()

    fun withTtl(newTtl: Int) = copy(ttl = newTtl)

    /** Identity used for de-duplication across every link. */
    val key: String get() = "${java.lang.Long.toHexString(senderId)}:${java.lang.Long.toHexString(messageId)}"

    private fun writeHeader(buf: ByteBuffer, ttlValue: Int) {
        buf.put(VERSION)
        buf.put(type.code)
        buf.put(ttlValue.toByte())
        buf.put(flags.toByte())
        buf.putLong(timestamp)
        buf.putLong(messageId)
        buf.putLong(senderId)
        buf.putLong(recipientId)
        buf.putInt(roomTag)
        buf.putShort(payload.size.toShort())
    }

    override fun equals(other: Any?): Boolean =
        other is MeshPacket && encode().contentEquals(other.encode())

    override fun hashCode(): Int = encode().contentHashCode()

    companion object {
        const val VERSION: Byte = 1
        const val HEADER_SIZE = 42
        const val BROADCAST = 0L
        const val DEFAULT_TTL = 7

        /** Largest packet we put on the air; fits a 512-byte negotiated BLE MTU. */
        const val MAX_PACKET_SIZE = 500

        fun decode(bytes: ByteArray): MeshPacket? {
            if (bytes.size < HEADER_SIZE || bytes.size > MAX_PACKET_SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            if (buf.get() != VERSION) return null
            val type = MeshType.fromCode(buf.get()) ?: return null
            val ttl = buf.get().toInt() and 0xFF
            val flags = buf.get().toInt() and 0xFF
            val timestamp = buf.long
            val messageId = buf.long
            val senderId = buf.long
            val recipientId = buf.long
            val roomTag = buf.int
            val length = buf.short.toInt() and 0xFFFF
            if (length != bytes.size - HEADER_SIZE) return null
            val payload = ByteArray(length).also { buf.get(it) }
            return MeshPacket(type, ttl, flags, timestamp, messageId, senderId, recipientId, roomTag, payload)
        }
    }
}
