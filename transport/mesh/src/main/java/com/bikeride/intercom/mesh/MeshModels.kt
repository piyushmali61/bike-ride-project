package com.bikeride.intercom.mesh

/** Rider profile shown to the convoy. Independent from the technical [ConvoyMesh.myId]. */
data class RiderProfile(
    val name: String = "Rider",
    val status: String = "Ready to ride",
    val avatar: String = "🏍️",
    val colorIndex: Int = 0,
    val bikeName: String = "",
    val bikeModel: String = "",
    val bikeNickname: String = "",
    val bikePlate: String = "",
    val bikeImagePath: String? = null
) {
    fun encode(): ByteArray = listOf(
        name, status, avatar, colorIndex.toString(),
        bikeName, bikeModel, bikeNickname, bikePlate
    ).joinToString(SEP).toByteArray(Charsets.UTF_8)

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
                colorIndex = (parts[3].toIntOrNull() ?: 0).mod(COLOR_COUNT),
                bikeName = parts.getOrNull(4)?.take(30) ?: "",
                bikeModel = parts.getOrNull(5)?.take(30) ?: "",
                bikeNickname = parts.getOrNull(6)?.take(30) ?: "",
                bikePlate = parts.getOrNull(7)?.take(20) ?: ""
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
    val rawPacket: String? = null,
    /** Local JPEG file for photo messages (null while still arriving). */
    val imagePath: String? = null,
    /** Photo pieces received so far / expected, for the "receiving photo" progress. */
    val imageReceived: Int = 0,
    val imageTotal: Int = 0,
    val locationName: String? = null,
    val isPictureStop: Boolean = false,
    val destinationName: String? = null
)

data class MeshPeer(
    val id: Long,
    val profile: RiderProfile,
    val lastSeen: Long,
    val hops: Int,
    val via: Via
)

/** Largest photo sent over the mesh (compressed JPEG), about 40 packets. */
const val MAX_PHOTO_BYTES = 16_000

/**
 * One piece of a photo. Photos are compressed to a few KB and split so each piece fits in one
 * mesh packet; pieces relay, de-duplicate and sync over the internet like any other packet.
 *
 * ```
 * imageId:8 index:2 total:2 nameLength:1 name:N data:M
 * ```
 */
internal data class ImageChunk(val imageId: Long, val index: Int, val total: Int, val senderName: String, val data: ByteArray) {
    fun encode(): ByteArray {
        val name = senderName.toByteArray(Charsets.UTF_8).take(60).toByteArray()
        return java.nio.ByteBuffer.allocate(13 + name.size + data.size)
            .putLong(imageId).putShort(index.toShort()).putShort(total.toShort())
            .put(name.size.toByte()).put(name).put(data).array()
    }

    companion object {
        const val HEADER = 13
        const val MAX_IMAGE_BYTES = MAX_PHOTO_BYTES
        const val MAX_CHUNKS = 64

        fun dataCapacity(senderName: String) =
            RoomCipher.MAX_PLAINTEXT - HEADER - senderName.toByteArray(Charsets.UTF_8).take(60).size

        fun decode(bytes: ByteArray): ImageChunk? {
            if (bytes.size < HEADER) return null
            val buf = java.nio.ByteBuffer.wrap(bytes)
            val id = buf.long
            val index = buf.short.toInt() and 0xFFFF
            val total = buf.short.toInt() and 0xFFFF
            val nameLen = buf.get().toInt() and 0xFF
            if (total == 0 || total > MAX_CHUNKS || index >= total || buf.remaining() < nameLen) return null
            val name = ByteArray(nameLen).also { buf.get(it) }.toString(Charsets.UTF_8)
            val data = ByteArray(buf.remaining()).also { buf.get(it) }
            return ImageChunk(id, index, total, name, data)
        }
    }
}

/** Body of CHAT / SOS / LOCATION / DESTINATION packets: sender name travels with the message. */
internal object MessageBody {
    fun encode(senderName: String, body: String) = "$senderName${RiderProfile.SEP}$body".toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Pair<String, String> {
        val text = bytes.toString(Charsets.UTF_8)
        val i = text.indexOf(RiderProfile.SEP)
        return if (i < 0) "Rider" to text else text.substring(0, i).take(20) to text.substring(i + 1)
    }

    fun parseLatLon(body: String): Pair<Double, Double>? {
        val main = body.split("|")[0]
        val parts = main.split(",")
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        return lat to lon
    }

    fun parseExtra(body: String): String? {
        val parts = body.split("|")
        return if (parts.size > 1) parts[1].trim().takeIf { it.isNotBlank() } else null
    }
}

