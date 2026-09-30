package com.bikeride.intercom.mesh

/**
 * Pure routing rules for the convoy mesh (no Android, no radio):
 *
 * - **Freshness**: drop packets older than [maxAgeMs] or from the future (replay protection).
 * - **De-duplication**: each (sender, messageId) is handled once, even when it arrives over
 *   several links (BLE from two neighbours, and again from the internet).
 * - **Hop limit**: a relayed copy has TTL - 1; at TTL 1 the packet stops.
 * - **Store-and-forward**: recent packets are kept and replayed to newly met neighbours, so a
 *   rider who was out of range catches up as soon as anyone who has the message comes near.
 */
class MeshRouter(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 6 * 60 * 60 * 1000L,
    private val seenCapacity: Int = 4096,
    private val bufferCapacity: Int = 600
) {
    private val seen = object : LinkedHashMap<String, Long>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > seenCapacity
    }
    private val buffer = LinkedHashMap<String, MeshPacket>()

    /** True when the packet is fresh and has not been handled before. Marks it as seen. */
    @Synchronized
    fun accept(packet: MeshPacket): Boolean {
        val t = now()
        if (packet.timestamp < t - maxAgeMs || packet.timestamp > t + FUTURE_SKEW_MS) return false
        if (seen.containsKey(packet.key)) return false
        seen[packet.key] = t
        return true
    }

    @Synchronized
    fun hasSeen(packet: MeshPacket) = seen.containsKey(packet.key)

    /** Marks our own packets as seen so their echoes are ignored. */
    @Synchronized
    fun markSeen(packet: MeshPacket) = markSeen(packet.key)

    /** Marks a stored message's key as seen, so history replayed after a restart is not duplicated. */
    @Synchronized
    fun markSeen(key: String) {
        seen[key] = now()
    }

    /** The copy to pass on to other neighbours, or null when the hop limit is used up. */
    fun relayCopy(packet: MeshPacket): MeshPacket? =
        if (packet.ttl > 1) packet.withTtl(packet.ttl - 1) else null

    /** Keeps a packet for replay to riders we meet later. */
    @Synchronized
    fun remember(packet: MeshPacket) {
        buffer.remove(packet.key)
        buffer[packet.key] = packet
        while (buffer.size > bufferCapacity) buffer.remove(buffer.keys.first())
    }

    /** Recent packets (oldest first) to send to a neighbour we have just connected to. */
    @Synchronized
    fun replayable(): List<MeshPacket> {
        val cutoff = now() - maxAgeMs
        buffer.values.removeAll { it.timestamp < cutoff }
        return buffer.values.toList()
    }

    /** Removes a specific message key from replay buffer and seen set so it never reappears. */
    @Synchronized
    fun forget(key: String) {
        buffer.remove(key)
        seen.remove(key)
    }

    /** Purges all packets for a deleted room from buffer and seen set. */
    @Synchronized
    fun clearRoom(roomTag: Int) {
        val keys = buffer.values.filter { it.roomTag == roomTag }.map { it.key }
        keys.forEach { key ->
            buffer.remove(key)
            seen.remove(key)
        }
    }

    companion object {
        const val FUTURE_SKEW_MS = 5 * 60 * 1000L
    }
}
