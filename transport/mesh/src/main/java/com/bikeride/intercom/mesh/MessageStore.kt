package com.bikeride.intercom.mesh

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** Offline-first storage: one JSON file of recent messages per room. */
class MessageStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    private fun file(roomTag: Int) = File(dir, "room_%08x.json".format(roomTag))

    fun load(roomTag: Int): List<ChatMessage> = try {
        val f = file(roomTag)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        Timber.w(e, "Mesh: could not read message store")
        emptyList()
    }

    fun save(roomTag: Int, messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.takeLast(MAX_MESSAGES).forEach { arr.put(toJson(it)) }
            val target = file(roomTag)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(target)
        } catch (e: Exception) {
            Timber.w(e, "Mesh: could not save message store")
        }
    }

    fun deleteRoom(roomTag: Int): Boolean = try {
        val target = file(roomTag)
        val tmp = File(dir, target.name + ".tmp")
        if (tmp.exists()) tmp.delete()
        if (target.exists()) target.delete() else true
    } catch (e: Exception) {
        Timber.w(e, "Mesh: could not delete room store")
        false
    }

    fun deleteMessage(roomTag: Int, key: String): Boolean = try {
        val current = load(roomTag).filterNot { it.key == key }
        save(roomTag, current)
        true
    } catch (e: Exception) {
        Timber.w(e, "Mesh: could not delete message from store")
        false
    }

    private fun toJson(m: ChatMessage) = JSONObject().apply {
        put("key", m.key)
        put("messageId", m.messageId)
        put("senderId", m.senderId)
        put("senderName", m.senderName)
        put("type", m.type.name)
        put("text", m.text)
        m.latitude?.let { put("lat", it) }
        m.longitude?.let { put("lon", it) }
        put("timestamp", m.timestamp)
        put("isMine", m.isMine)
        put("state", m.state.name)
        put("seenBy", JSONArray(m.seenBy.toList()))
        put("via", m.via.name)
        put("hops", m.hops)
        m.rawPacket?.let { put("raw", it) }
        m.imagePath?.let { put("image", it) }
        put("imgRx", m.imageReceived)
        put("imgTotal", m.imageTotal)
        m.locationName?.let { put("locName", it) }
        put("isPicStop", m.isPictureStop)
        m.destinationName?.let { put("destName", it) }
    }

    private fun fromJson(o: JSONObject): ChatMessage? = try {
        val seen = o.optJSONArray("seenBy")
        ChatMessage(
            key = o.getString("key"),
            messageId = o.getLong("messageId"),
            senderId = o.getLong("senderId"),
            senderName = o.getString("senderName"),
            type = MeshType.valueOf(o.getString("type")),
            text = o.getString("text"),
            latitude = if (o.has("lat")) o.getDouble("lat") else null,
            longitude = if (o.has("lon")) o.getDouble("lon") else null,
            timestamp = o.getLong("timestamp"),
            isMine = o.getBoolean("isMine"),
            state = DeliveryState.valueOf(o.getString("state")),
            seenBy = if (seen == null) emptySet() else (0 until seen.length()).map { seen.getLong(it) }.toSet(),
            via = Via.valueOf(o.getString("via")),
            hops = o.optInt("hops"),
            rawPacket = if (o.has("raw")) o.getString("raw") else null,
            imagePath = if (o.has("image")) o.getString("image") else null,
            imageReceived = o.optInt("imgRx"),
            imageTotal = o.optInt("imgTotal"),
            locationName = if (o.has("locName")) o.getString("locName") else null,
            isPictureStop = o.optBoolean("isPicStop", false),
            destinationName = if (o.has("destName")) o.getString("destName") else null
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_MESSAGES = 500
    }
}
