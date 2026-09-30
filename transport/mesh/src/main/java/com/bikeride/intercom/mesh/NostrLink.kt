package com.bikeride.intercom.mesh

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Internet path for the convoy mesh over free public Nostr relays — no server of our own.
 *
 * Each mesh packet (already room-encrypted) is published as a Nostr event whose content is the
 * base64 packet and whose `t` tag is the room's [RoomCipher.internetTag]. Every rider with data
 * subscribes to that tag, so the same packet reaches far-away riders, and riders who were
 * offline catch up from the relays' stored history when they come back online.
 */
class NostrLink(
    private val secretKey: ByteArray,
    private val onReceive: (bytes: ByteArray) -> Unit,
    private val onConnected: () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val publicKeyHex = Schnorr.publicKey(secretKey).toHex()
    private val random = SecureRandom()

    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private var scope: CoroutineScope? = null
    private var roomTag: String? = null

    private val _connectedRelays = MutableStateFlow(0)
    val connectedRelays: StateFlow<Int> = _connectedRelays.asStateFlow()

    fun start(parent: CoroutineScope, tag: String) {
        stop()
        roomTag = tag
        val s = CoroutineScope(parent.coroutineContext + SupervisorJob() + Dispatchers.IO)
        scope = s
        RELAYS.forEach { url -> s.launch { keepConnected(url, tag) } }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        sockets.values.forEach { it.close(1000, "bye") }
        sockets.clear()
        _connectedRelays.value = 0
    }

    /** Publishes one mesh packet to every connected relay. */
    fun publish(packetBytes: ByteArray) {
        val tag = roomTag ?: return
        if (sockets.isEmpty()) return
        val event = try {
            buildEvent(Base64.getEncoder().encodeToString(packetBytes), tag)
        } catch (e: Exception) {
            Timber.w(e, "Nostr: signing failed")
            return
        }
        val frame = JSONArray().put("EVENT").put(event).toString()
        sockets.values.forEach { it.send(frame) }
    }

    private suspend fun keepConnected(url: String, tag: String) {
        var backoff = 5_000L
        while (currentCoroutineContext().isActive) {
            val closed = CompletableDeferred<Unit>()
            val request = Request.Builder().url(url).build()
            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sockets[url] = webSocket
                    _connectedRelays.value = sockets.size
                    backoff = 5_000L
                    val filter = JSONObject()
                        .put("kinds", JSONArray().put(EVENT_KIND))
                        .put("#t", JSONArray().put(tag))
                        .put("since", System.currentTimeMillis() / 1000 - HISTORY_SECONDS)
                        .put("limit", 300)
                    webSocket.send(JSONArray().put("REQ").put("astra").put(filter).toString())
                    Timber.i("Nostr: connected $url")
                    onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONArray(text)
                        if (msg.optString(0) == "OK") {
                            Timber.i("Nostr: $url ${if (msg.optBoolean(2)) "accepted" else "rejected (${msg.optString(3)})"} event")
                            return
                        }
                        if (msg.optString(0) != "EVENT") return
                        val event = msg.getJSONObject(2)
                        if (event.optInt("kind") != EVENT_KIND) return
                        onReceive(Base64.getDecoder().decode(event.getString("content")))
                    } catch (e: Exception) {
                        Timber.d(e, "Nostr: bad message from $url")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = drop()
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = drop()

                private fun drop() {
                    sockets.remove(url)
                    _connectedRelays.value = sockets.size
                    closed.complete(Unit)
                }
            })
            closed.await()
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun buildEvent(content: String, tag: String): JSONObject {
        val createdAt = System.currentTimeMillis() / 1000
        val tags = JSONArray().put(JSONArray().put("t").put(tag))
        val serialized = JSONArray()
            .put(0).put(publicKeyHex).put(createdAt).put(EVENT_KIND).put(tags).put(content)
            .toString()
            .replace("\\/", "/") // Nostr ids hash the canonical form; org.json escapes '/'
        val id = MessageDigest.getInstance("SHA-256").digest(serialized.toByteArray())
        val aux = ByteArray(32).also { random.nextBytes(it) }
        val sig = Schnorr.sign(id, secretKey, aux)
        return JSONObject()
            .put("id", id.toHex())
            .put("pubkey", publicKeyHex)
            .put("created_at", createdAt)
            .put("kind", EVENT_KIND)
            .put("tags", tags)
            .put("content", content)
            .put("sig", sig.toHex())
    }

    companion object {
        /** Regular (stored) event kind used only by AstraRide convoy packets. */
        const val EVENT_KIND = 4373
        const val HISTORY_SECONDS = 6 * 60 * 60L
        val RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band",
            "wss://relay.primal.net"
        )
    }
}

internal fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
