package com.bikeride.intercom.mesh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConvoyMesh: offline-first convoy messaging.
 *
 * ```
 *  UI ──► ConvoyMesh ──► MeshRouter (dedup · TTL · store-and-forward)
 *                      ├─► BleMeshLink  (phone ⇄ phone, multi-hop, no internet)
 *                      └─► NostrLink    (public relays when any data is available)
 * ```
 *
 * The same encrypted packet travels over both paths, so switching between offline and online
 * needs no separate account and cannot create duplicates: every packet is handled once by its
 * (sender, messageId). A phone that has internet also bridges packets it hears over Bluetooth
 * up to the relays, so offline riders nearby reach far-away riders through it.
 */
@Singleton
class ConvoyMesh @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("astra_ride_mesh", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serial)

    /** Technical identity of this install; never shown to users. */
    val myId: Long = prefs.getLong(KEY_ID, 0L).takeIf { it != 0L } ?: newId().also {
        prefs.edit().putLong(KEY_ID, it).apply()
    }

    private val router = MeshRouter()
    private val store = MessageStore(File(context.filesDir, "mesh"))
    private var cipher: RoomCipher? = null
    private var started = false

    private val ble = BleMeshLink(
        context,
        onReceive = { linkId, bytes -> scope.launch { handleIncoming(bytes, linkId) } },
        onLinkUp = { linkId -> scope.launch { onNewNeighbour(linkId) } }
    )
    private val nostr = NostrLink(
        secretKey = loadNostrKey(),
        onReceive = { bytes -> scope.launch { handleIncoming(bytes, INTERNET_LINK) } },
        onConnected = { scope.launch { onInternetUp() } }
    )

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<RiderProfile> = _profile.asStateFlow()

    private val _room = MutableStateFlow("")
    val room: StateFlow<String> = _room.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _peers = MutableStateFlow<Map<Long, MeshPeer>>(emptyMap())
    val peers: StateFlow<Map<Long, MeshPeer>> = _peers.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _incomingSos = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 4)
    val incomingSos: SharedFlow<ChatMessage> = _incomingSos.asSharedFlow()

    /** Fresh messages from other riders (for spoken announcements). Old history is not re-emitted. */
    private val _incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    val incoming: SharedFlow<ChatMessage> = _incoming.asSharedFlow()

    /** Photos still arriving: key -> pieces received so far. */
    private val imageParts = LinkedHashMap<String, Array<ByteArray?>>()
    private val imageDir = File(context.filesDir, "mesh/img").apply { mkdirs() }

    val bluetoothLinks: StateFlow<Int> = ble.linkCount
    val bluetoothRunning: StateFlow<Boolean> = ble.isRunning
    val internetRelays: StateFlow<Int> = nostr.connectedRelays

    private var chatVisible = false

    init {
        setRoom(prefs.getString(KEY_ROOM, null) ?: "CONVOY 1")
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    /** Starts radios. Safe to call again, e.g. after permissions are granted or Bluetooth is switched on. */
    fun start() = scope.launch {
        ble.stop()
        ble.start(scope)
        if (!started) {
            started = true
            cipher?.let { nostr.start(scope, it.internetTag) }
            launch { announceLoop() }
        }
    }

    fun setRoom(code: String) = scope.launch {
        val normalized = RoomCipher.normalize(code)
        if (normalized.isBlank() || normalized == _room.value) return@launch
        val newCipher = RoomCipher(normalized) // key derivation: off the main thread
        cipher = newCipher
        _room.value = normalized
        prefs.edit().putString(KEY_ROOM, normalized).apply()
        _peers.value = emptyMap()
        _unread.value = 0

        val stored = store.load(newCipher.tag)
        stored.forEach { router.markSeen(it.key) }
        deletedKeys().forEach { router.markSeen(it) }
        _messages.value = stored
        // Store-and-forward survives restarts: our recent messages go back into the replay buffer.
        stored.filter { it.isMine && it.rawPacket != null }.forEach { msg ->
            MeshPacket.decode(Base64.getDecoder().decode(msg.rawPacket))?.let { router.remember(it) }
        }

        if (started) {
            nostr.start(scope, newCipher.internetTag)
            announceProfile(toInternet = true)
        }
    }

    private fun deletedBefore(roomTag: Int): Long = prefs.getLong(KEY_DELETED_BEFORE + roomTag, 0L)

    private fun deletedKeys(): List<String> =
        prefs.getString(KEY_DELETED_KEYS, "")!!.split(",").filter { it.isNotBlank() }

    private fun rememberDeleted(key: String) {
        val keys = (deletedKeys() + key).distinct().takeLast(MAX_DELETED_KEYS)
        prefs.edit().putString(KEY_DELETED_KEYS, keys.joinToString(",")).apply()
    }

    suspend fun deleteConvoyData(code: String = _room.value): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalized = RoomCipher.normalize(code)
            val roomCipher = RoomCipher(normalized)
            val deleted = store.deleteRoom(roomCipher.tag)
            router.clearRoom(roomCipher.tag)
            // Persist the deletion: anything older than now in this room is ignored if it is re-sent
            prefs.edit().putLong(KEY_DELETED_BEFORE + roomCipher.tag, System.currentTimeMillis()).apply()
            if (normalized.equals(_room.value, ignoreCase = true)) {
                _messages.value = emptyList()
                _unread.value = 0
                _peers.value = emptyMap()
            }
            deleted
        } catch (e: Exception) {
            Timber.w(e, "Mesh: could not delete convoy data")
            false
        }
    }

    suspend fun deleteMessage(messageKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val msg = _messages.value.find { it.key == messageKey }
            msg?.imagePath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            router.forget(messageKey)
            rememberDeleted(messageKey)
            val deleted = cipher?.let { store.deleteMessage(it.tag, messageKey) } ?: false
            _messages.value = _messages.value.filterNot { it.key == messageKey }
            deleted
        } catch (e: Exception) {
            Timber.w(e, "Mesh: could not delete message")
            false
        }
    }

    suspend fun deleteBikeProfile(): Boolean = withContext(Dispatchers.IO) {
        try {
            _profile.value.bikeImagePath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            val appPrefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
            appPrefs.edit()
                .remove("BIKE_NAME")
                .remove("BIKE_MODEL")
                .commit()
            prefs.edit()
                .remove(KEY_BIKE_NAME)
                .remove(KEY_BIKE_MODEL)
                .remove(KEY_BIKE_NICKNAME)
                .remove(KEY_BIKE_PLATE)
                .remove(KEY_BIKE_IMAGE)
                .commit()
            _profile.value = _profile.value.copy(
                bikeName = "",
                bikeModel = "",
                bikeNickname = "",
                bikePlate = "",
                bikeImagePath = null
            )
            announceProfile(toInternet = true)
            true
        } catch (e: Exception) {
            Timber.w(e, "Mesh: could not delete bike profile")
            false
        }
    }

    suspend fun deleteRiderProfile(): Boolean = withContext(Dispatchers.IO) {
        try {
            val appPrefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
            appPrefs.edit()
                .remove("RIDER_NAME")
                .remove("BIKE_MODEL")
                .commit()
            prefs.edit()
                .remove(KEY_NAME)
                .remove(KEY_STATUS)
                .remove(KEY_AVATAR)
                .remove(KEY_COLOR)
                .commit()
            _profile.value = _profile.value.copy(
                name = "Rider",
                status = "Ready to ride",
                avatar = "🏍️",
                colorIndex = 0
            )
            announceProfile(toInternet = true)
            true
        } catch (e: Exception) {
            Timber.w(e, "Mesh: could not delete rider profile")
            false
        }
    }

    fun setChatVisible(visible: Boolean) {
        chatVisible = visible
        if (visible) _unread.value = 0
    }

    // ── Sending ─────────────────────────────────────────────────────

    fun updateProfile(profile: RiderProfile) = scope.launch {
        val clean = profile.copy(
            name = profile.name.trim().take(20).ifBlank { "Rider" },
            status = profile.status.trim().take(40),
            bikeName = profile.bikeName.trim().take(30),
            bikeModel = profile.bikeModel.trim().take(30),
            bikeNickname = profile.bikeNickname.trim().take(30),
            bikePlate = profile.bikePlate.trim().take(20)
        )
        _profile.value = clean
        prefs.edit()
            .putString(KEY_NAME, clean.name)
            .putString(KEY_STATUS, clean.status)
            .putString(KEY_AVATAR, clean.avatar)
            .putInt(KEY_COLOR, clean.colorIndex)
            .putString(KEY_BIKE_NAME, clean.bikeName)
            .putString(KEY_BIKE_MODEL, clean.bikeModel)
            .putString(KEY_BIKE_NICKNAME, clean.bikeNickname)
            .putString(KEY_BIKE_PLATE, clean.bikePlate)
            .putString(KEY_BIKE_IMAGE, clean.bikeImagePath)
            .apply()
        announceProfile(toInternet = true)
    }

    fun sendChat(text: String) = scope.launch {
        val body = text.trim()
        if (body.isEmpty()) return@launch
        sendMessage(MeshType.CHAT, fitBody(body), null, null)
    }

    fun sendLocation(latitude: Double, longitude: Double, placeName: String? = null) = scope.launch {
        val body = if (placeName != null) {
            "%.6f,%.6f|$placeName".format(java.util.Locale.US, latitude, longitude)
        } else {
            "%.6f,%.6f".format(java.util.Locale.US, latitude, longitude)
        }
        val name = _profile.value.name
        val packet = originate(MeshType.LOCATION, MessageBody.encode(name, body)) ?: return@launch
        val online = ble.linkCount.value > 0 || nostr.connectedRelays.value > 0
        addMessage(
            ChatMessage(
                key = packet.key,
                messageId = packet.messageId,
                senderId = myId,
                senderName = name,
                type = MeshType.LOCATION,
                text = body,
                latitude = latitude,
                longitude = longitude,
                locationName = placeName,
                timestamp = packet.timestamp,
                isMine = true,
                state = if (online) DeliveryState.SENT else DeliveryState.QUEUED,
                via = Via.YOU,
                rawPacket = Base64.getEncoder().encodeToString(packet.encode())
            )
        )
    }

    fun sendDestinationReached(destinationName: String, lat: Double, lon: Double) = scope.launch {
        val name = _profile.value.name
        val body = "$destinationName|%.6f,%.6f".format(java.util.Locale.US, lat, lon)
        val packet = originate(MeshType.DESTINATION, MessageBody.encode(name, body)) ?: return@launch
        val online = ble.linkCount.value > 0 || nostr.connectedRelays.value > 0
        addMessage(
            ChatMessage(
                key = packet.key,
                messageId = packet.messageId,
                senderId = myId,
                senderName = name,
                type = MeshType.DESTINATION,
                text = "Reached $destinationName",
                latitude = lat,
                longitude = lon,
                destinationName = destinationName,
                timestamp = packet.timestamp,
                isMine = true,
                state = if (online) DeliveryState.SENT else DeliveryState.QUEUED,
                via = Via.YOU,
                rawPacket = Base64.getEncoder().encodeToString(packet.encode())
            )
        )
    }

    fun sendSos(latitude: Double?, longitude: Double?) = scope.launch {
        val body = if (latitude != null && longitude != null) {
            "%.6f,%.6f".format(java.util.Locale.US, latitude, longitude)
        } else ""
        sendMessage(MeshType.SOS, body, latitude, longitude)
    }

    /**
     * Sends a photo that is already compressed to at most [ImageChunk.MAX_IMAGE_BYTES] (JPEG).
     * Returns false when it is too large.
     */
    fun sendImage(
        jpeg: ByteArray,
        isPictureStop: Boolean = false,
        locationName: String? = null,
        lat: Double? = null,
        lon: Double? = null
    ): Boolean {
        if (jpeg.isEmpty() || jpeg.size > ImageChunk.MAX_IMAGE_BYTES) return false
        scope.launch {
            val name = _profile.value.name
            val capacity = ImageChunk.dataCapacity(name)
            val total = (jpeg.size + capacity - 1) / capacity
            if (total > ImageChunk.MAX_CHUNKS) return@launch
            val imageId = newId()
            val key = imageKey(myId, imageId)
            val file = File(imageDir, "$key.jpg").apply { writeBytes(jpeg) }
            val online = ble.linkCount.value > 0 || nostr.connectedRelays.value > 0
            val textLabel = if (isPictureStop) "Picture Stop" else "Photo"
            addMessage(
                ChatMessage(
                    key = key, messageId = imageId, senderId = myId, senderName = name,
                    type = MeshType.IMAGE, text = textLabel, timestamp = System.currentTimeMillis(),
                    isMine = true, state = if (online) DeliveryState.SENT else DeliveryState.QUEUED,
                    via = Via.YOU, imagePath = file.absolutePath, imageReceived = total, imageTotal = total,
                    isPictureStop = isPictureStop, locationName = locationName, latitude = lat, longitude = lon
                )
            )
            for (i in 0 until total) {
                val data = jpeg.copyOfRange(i * capacity, minOf(jpeg.size, (i + 1) * capacity))
                originate(MeshType.IMAGE, ImageChunk(imageId, i, total, name, data).encode())
                delay(IMAGE_CHUNK_PACING_MS) // lets Bluetooth queues drain between pieces
            }
        }
        return true
    }

    fun sendPhoto(
        jpeg: ByteArray,
        locationName: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        isPictureStop: Boolean = false
    ): Boolean = sendImage(
        jpeg = jpeg,
        isPictureStop = isPictureStop,
        locationName = locationName,
        lat = lat,
        lon = lon
    )

    private fun imageKey(sender: Long, imageId: Long) =
        "img:${java.lang.Long.toHexString(sender)}:${java.lang.Long.toHexString(imageId)}"

    private fun sendMessage(type: MeshType, body: String, lat: Double?, lon: Double?) {
        val name = _profile.value.name
        val packet = originate(type, MessageBody.encode(name, body)) ?: return
        val online = ble.linkCount.value > 0 || nostr.connectedRelays.value > 0
        addMessage(
            ChatMessage(
                key = packet.key,
                messageId = packet.messageId,
                senderId = myId,
                senderName = name,
                type = type,
                text = body,
                latitude = lat,
                longitude = lon,
                timestamp = packet.timestamp,
                isMine = true,
                state = if (online) DeliveryState.SENT else DeliveryState.QUEUED,
                via = Via.YOU,
                rawPacket = Base64.getEncoder().encodeToString(packet.encode())
            )
        )
    }

    /** Keeps a chat body inside one packet (name + text must fit [RoomCipher.MAX_PLAINTEXT]). */
    private fun fitBody(text: String): String {
        var body = text.take(MAX_CHAT_CHARS)
        val nameBytes = MessageBody.encode(_profile.value.name, "").size
        while (nameBytes + body.toByteArray().size > RoomCipher.MAX_PLAINTEXT) body = body.dropLast(1)
        return body
    }

    /** Builds, encrypts and sends a new packet from this phone. */
    private fun originate(
        type: MeshType,
        plain: ByteArray,
        recipient: Long = MeshPacket.BROADCAST,
        ttl: Int = MeshPacket.DEFAULT_TTL,
        keepForReplay: Boolean = true,
        toInternet: Boolean = true
    ): MeshPacket? {
        val c = cipher ?: return null
        if (plain.size > RoomCipher.MAX_PLAINTEXT) return null
        val header = MeshPacket(
            type = type,
            ttl = ttl,
            timestamp = System.currentTimeMillis(),
            messageId = newId(),
            senderId = myId,
            recipientId = recipient,
            roomTag = c.tag,
            payload = ByteArray(plain.size + RoomCipher.OVERHEAD)
        )
        val packet = header.copy(payload = c.seal(plain, header.aad()))
        router.markSeen(packet)
        if (keepForReplay) router.remember(packet)
        val bytes = packet.encode()
        ble.broadcast(bytes)
        if (toInternet) nostr.publish(bytes)
        return packet
    }

    private fun announceProfile(toInternet: Boolean) {
        originate(MeshType.PROFILE, _profile.value.encode(), ttl = PROFILE_TTL, keepForReplay = false, toInternet = toInternet)
    }

    private suspend fun announceLoop() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            announceProfile(toInternet = tick % 12 == 0) // Bluetooth every 45 s, internet every 9 min
            val cutoff = System.currentTimeMillis() - PEER_FORGET_MS
            _peers.value = _peers.value.filterValues { it.lastSeen > cutoff }
            tick++
            delay(45_000)
        }
    }

    // ── Receiving ───────────────────────────────────────────────────

    private fun handleIncoming(bytes: ByteArray, sourceLink: String) {
        val packet = MeshPacket.decode(bytes) ?: return
        if (packet.senderId == myId) return
        if (!router.accept(packet)) return

        // 1. Relay: every phone is a node, even for rooms it cannot read.
        if (packet.type != MeshType.PROFILE) router.remember(packet)
        router.relayCopy(packet)?.let { ble.broadcast(it.encode(), exceptLinkId = sourceLink) }

        val c = cipher ?: return
        if (packet.roomTag != c.tag) return
        // Deleted history must not come back from relays or neighbours (checked before bridging)
        if (packet.timestamp < deletedBefore(c.tag) || packet.key in deletedKeys()) return

        // 2. Bridge: offline riders' messages go up to the internet through us.
        if (sourceLink != INTERNET_LINK && packet.type != MeshType.PROFILE) nostr.publish(bytes)

        // 3. Read it.
        val plain = c.open(packet.payload, packet.aad()) ?: return
        val via = if (sourceLink == INTERNET_LINK) Via.INTERNET else Via.BLUETOOTH
        val hops = if (via == Via.BLUETOOTH) (MeshPacket.DEFAULT_TTL - packet.ttl + 1).coerceAtLeast(1) else 0

        when (packet.type) {
            MeshType.PROFILE -> RiderProfile.decode(plain)?.let { touchPeer(packet.senderId, it, via, hops) }
            MeshType.ACK -> if (packet.recipientId == myId && plain.size >= 8) {
                val ref = ByteBuffer.wrap(plain).long
                updateMessages { list ->
                    list.map { if (it.isMine && it.messageId == ref) it.copy(seenBy = it.seenBy + packet.senderId, state = DeliveryState.SENT) else it }
                }
            }
            MeshType.IMAGE -> receiveImageChunk(packet, plain, via, hops)
            MeshType.CHAT, MeshType.SOS, MeshType.LOCATION, MeshType.DESTINATION -> {
                if (_messages.value.any { it.key == packet.key }) return
                val (name, body) = MessageBody.decode(plain)
                val latLon = when (packet.type) {
                    MeshType.CHAT -> null
                    MeshType.DESTINATION -> {
                        val extra = MessageBody.parseExtra(body)
                        extra?.let { MessageBody.parseLatLon(it) } ?: MessageBody.parseLatLon(body)
                    }
                    else -> MessageBody.parseLatLon(body)
                }
                val destName = if (packet.type == MeshType.DESTINATION) body.split("|")[0].trim() else null
                val locName = if (packet.type == MeshType.LOCATION) MessageBody.parseExtra(body) else null
                val displayText = when (packet.type) {
                    MeshType.DESTINATION -> if (!destName.isNullOrBlank()) "Reached $destName" else "Reached destination"
                    else -> body
                }
                val msg = ChatMessage(
                    key = packet.key,
                    messageId = packet.messageId,
                    senderId = packet.senderId,
                    senderName = name,
                    type = packet.type,
                    text = displayText,
                    latitude = latLon?.first,
                    longitude = latLon?.second,
                    locationName = locName,
                    destinationName = destName,
                    timestamp = packet.timestamp,
                    isMine = false,
                    via = via,
                    hops = hops
                )
                addMessage(msg)
                touchPeer(packet.senderId, _peers.value[packet.senderId]?.profile ?: RiderProfile(name = name), via, hops)
                if (!chatVisible) _unread.value += 1
                // Only a fresh SOS sounds the alarm; old ones synced from history stay silent
                if (packet.type == MeshType.SOS && isFresh(packet)) _incomingSos.tryEmit(msg)
                if (isFresh(packet)) _incoming.tryEmit(msg)
                // Delivery receipt back to the sender, over whichever path works.
                originate(
                    MeshType.ACK,
                    ByteBuffer.allocate(8).putLong(packet.messageId).array(),
                    recipient = packet.senderId,
                    keepForReplay = false
                )
            }
        }
    }

    private fun receiveImageChunk(packet: MeshPacket, plain: ByteArray, via: Via, hops: Int) {
        val chunk = ImageChunk.decode(plain) ?: return
        val key = imageKey(packet.senderId, chunk.imageId)
        val existing = _messages.value.firstOrNull { it.key == key }
        if (existing?.imagePath != null) return

        val parts = imageParts.getOrPut(key) { arrayOfNulls(chunk.total) }
        if (parts.size != chunk.total) return
        parts[chunk.index] = chunk.data
        while (imageParts.size > MAX_PENDING_IMAGES) imageParts.remove(imageParts.keys.first())
        val received = parts.count { it != null }

        var path: String? = null
        if (received == chunk.total) {
            val file = File(imageDir, "$key.jpg")
            file.outputStream().use { out -> parts.forEach { out.write(it!!) } }
            imageParts.remove(key)
            path = file.absolutePath
        }

        val msg = (existing ?: ChatMessage(
            key = key, messageId = chunk.imageId, senderId = packet.senderId, senderName = chunk.senderName,
            type = MeshType.IMAGE, text = "Photo", timestamp = packet.timestamp, isMine = false,
            via = via, hops = hops, imageTotal = chunk.total
        )).copy(imagePath = path, imageReceived = received)

        if (existing == null) {
            addMessage(msg)
            touchPeer(packet.senderId, _peers.value[packet.senderId]?.profile ?: RiderProfile(name = chunk.senderName), via, hops)
        } else {
            updateMessages { list -> list.map { if (it.key == key) msg else it } }
        }

        if (path != null) {
            if (!chatVisible) _unread.value += 1
            if (isFresh(packet)) _incoming.tryEmit(msg)
            originate(
                MeshType.ACK,
                ByteBuffer.allocate(8).putLong(chunk.imageId).array(),
                recipient = packet.senderId,
                keepForReplay = false
            )
        }
    }

    /** Only recent messages are announced; history replayed after reconnecting stays silent. */
    private fun isFresh(packet: MeshPacket) = System.currentTimeMillis() - packet.timestamp < ANNOUNCE_WINDOW_MS

    private fun onNewNeighbour(linkId: String) {
        // Store-and-forward: hand the newcomer everything recent it may have missed.
        router.replayable().forEach { ble.sendTo(linkId, it.encode()) }
        announceProfile(toInternet = false)
        markQueuedAsSent()
    }

    private fun onInternetUp() {
        val tag = cipher?.tag ?: return
        router.replayable().filter { it.roomTag == tag }.forEach { nostr.publish(it.encode()) }
        markQueuedAsSent()
    }

    private fun markQueuedAsSent() {
        if (_messages.value.none { it.state == DeliveryState.QUEUED }) return
        updateMessages { list -> list.map { if (it.state == DeliveryState.QUEUED) it.copy(state = DeliveryState.SENT) else it } }
    }

    private fun touchPeer(id: Long, profile: RiderProfile, via: Via, hops: Int) {
        _peers.value = _peers.value + (id to MeshPeer(id, profile, System.currentTimeMillis(), hops, via))
    }

    private fun addMessage(message: ChatMessage) = updateMessages { (it + message).sortedBy { m -> m.timestamp } }

    private fun updateMessages(transform: (List<ChatMessage>) -> List<ChatMessage>) {
        val updated = transform(_messages.value).takeLast(MessageStore.MAX_MESSAGES)
        _messages.value = updated
        cipher?.let { store.save(it.tag, updated) }
    }

    // ── Persistence helpers ─────────────────────────────────────────

    private fun loadProfile(): RiderProfile {
        val appPrefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
        return RiderProfile(
            name = prefs.getString(KEY_NAME, null)
                ?: appPrefs.getString("RIDER_NAME", "Rider")
                ?: "Rider",
            status = prefs.getString(KEY_STATUS, "Ready to ride") ?: "Ready to ride",
            avatar = prefs.getString(KEY_AVATAR, "🏍️") ?: "🏍️",
            colorIndex = prefs.getInt(KEY_COLOR, 0),
            bikeName = prefs.getString(KEY_BIKE_NAME, null) ?: appPrefs.getString("BIKE_NAME", "") ?: "",
            bikeModel = prefs.getString(KEY_BIKE_MODEL, null) ?: appPrefs.getString("BIKE_MODEL", "Yamaha R15 V4") ?: "Yamaha R15 V4",
            bikeNickname = prefs.getString(KEY_BIKE_NICKNAME, "") ?: "",
            bikePlate = prefs.getString(KEY_BIKE_PLATE, "") ?: "",
            bikeImagePath = prefs.getString(KEY_BIKE_IMAGE, null)
        )
    }

    private fun loadNostrKey(): ByteArray {
        prefs.getString(KEY_NOSTR, null)?.let { hex ->
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        while (true) {
            val key = ByteArray(32).also { random.nextBytes(it) }
            try {
                Schnorr.publicKey(key)
                prefs.edit().putString(KEY_NOSTR, key.toHex()).apply()
                return key
            } catch (e: IllegalArgumentException) {
                Timber.d("Nostr key out of range, retrying")
            }
        }
    }

    private fun newId(): Long {
        var id: Long
        do { id = random.nextLong() } while (id == 0L)
        return id
    }

    companion object {
        const val INTERNET_LINK = "internet"
        const val PROFILE_TTL = 3
        const val MAX_CHAT_CHARS = 300
        const val PEER_FORGET_MS = 10 * 60 * 1000L
        const val PEER_ACTIVE_MS = 3 * 60 * 1000L
        const val ANNOUNCE_WINDOW_MS = 2 * 60 * 1000L
        private const val IMAGE_CHUNK_PACING_MS = 25L
        private const val MAX_PENDING_IMAGES = 8

        private const val KEY_ID = "mesh_id"
        private const val KEY_ROOM = "mesh_room"
        private const val KEY_NAME = "profile_name"
        private const val KEY_STATUS = "profile_status"
        private const val KEY_AVATAR = "profile_avatar"
        private const val KEY_COLOR = "profile_color"
        private const val KEY_BIKE_NAME = "profile_bike_name"
        private const val KEY_BIKE_MODEL = "profile_bike_model"
        private const val KEY_BIKE_NICKNAME = "profile_bike_nickname"
        private const val KEY_BIKE_PLATE = "profile_bike_plate"
        private const val KEY_BIKE_IMAGE = "profile_bike_image"
        private const val KEY_NOSTR = "nostr_secret"
        private const val KEY_DELETED_BEFORE = "deleted_before_"
        private const val KEY_DELETED_KEYS = "deleted_keys"
        private const val MAX_DELETED_KEYS = 1000
    }
}
