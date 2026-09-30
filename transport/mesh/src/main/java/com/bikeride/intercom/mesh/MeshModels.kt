package com.bikeride.intercom.mesh

/** Rider profile shown to the convoy. Independent from the technical [ConvoyMesh.myId]. */
data class RiderProfile(
    val name: String = "Rider",
    val status: String = "Ready to ride",
    val avatar: String = "🏍️",
    val colorIndex: Int = 0
) {
    fun encode(): ByteArray = listOf(name, status, avatar, colorIndex.toString())
        .joinToString(SEP).toByteArray(Charsets.UTF_8)

    companion object {
        const val SEP = "\u001F"
        val AVATARS = listOf("🏍️", "🛵", "🦅", "🐺", "🦁", "🔥", "⚡", "🏁", "🌄", "🛣️", "👑", "🚀")
        const val COLOR_COUNT = 8

        fun decode(bytes: ByteArray): RiderProfile? {
            val parts = bytes.toString(Charsets.UTF_8).split(SEP)
            if (parts.size < 4) return null
            return RiderProfile(
                name = parts[0].take(20).ifBlank { "Rider" },
                status = parts[1].take(40),
                avatar = parts[2].take(8).ifBlank { "🏍️" },
                colorIndex = (parts[3].toIntOrNull() ?: 0).mod(COLOR_COUNT)
            )
        }
    }
}

enum class DeliveryState { QUEUED, SENT }

enum class Via { YOU, BLUETOOTH, INTERNET }

data class ChatMessage(
    val key: String,
    val messageId: Long,
    val senderId: Long,
    val senderName: String,
    val type: MeshType,
    val text: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val isMine: Boolean,
    val state: DeliveryState = DeliveryState.SENT,
    val seenBy: Set<Long> = emptySet(),
    val via: Via,
    val hops: Int = 0,
    /** Base64 of the encrypted packet, kept for our own messages so they can be re-sent. */
    val rawPacket: String? = null
)

data class MeshPeer(
    val id: Long,
    val profile: RiderProfile,
    val lastSeen: Long,
    val hops: Int,
    val via: Via
)

/** Body of CHAT / SOS / LOCATION packets: sender name travels with the message. */
internal object MessageBody {
    fun encode(senderName: String, body: String) = "$senderName${RiderProfile.SEP}$body".toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Pair<String, String> {
        val text = bytes.toString(Charsets.UTF_8)
        val i = text.indexOf(RiderProfile.SEP)
        return if (i < 0) "Rider" to text else text.substring(0, i).take(20) to text.substring(i + 1)
    }

    fun parseLatLon(body: String): Pair<Double, Double>? {
        val parts = body.split(",")
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        return lat to lon
    }
}
