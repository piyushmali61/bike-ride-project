package com.bikeride.intercom.mesh

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MeshCoreTest {

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun packet(ttl: Int = 7, ts: Long = 1_000_000L, id: Long = 42L, sender: Long = 7L) = MeshPacket(
        type = MeshType.CHAT, ttl = ttl, timestamp = ts, messageId = id, senderId = sender,
        roomTag = 0x12345678, payload = "hello".toByteArray()
    )

    // ── Packet codec ────────────────────────────────────────────────

    @Test
    fun `packet round-trips through the wire format`() {
        val p = packet().copy(recipientId = -99L, flags = 3)
        val decoded = MeshPacket.decode(p.encode())
        assertEquals(p, decoded)
        assertEquals(MeshPacket.HEADER_SIZE + 5, p.encode().size)
    }

    @Test
    fun `corrupt or truncated packets are rejected`() {
        val bytes = packet().encode()
        assertNull(MeshPacket.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(MeshPacket.decode(bytes.copyOf(10)))
        assertNull(MeshPacket.decode(bytes.clone().also { it[0] = 9 }))  // unknown version
        assertNull(MeshPacket.decode(bytes.clone().also { it[1] = 99 })) // unknown type
    }

    @Test
    fun `authenticated header ignores ttl only`() {
        assertArrayEquals(packet(ttl = 7).aad(), packet(ttl = 2).aad())
        assertFalse(packet(id = 1).aad().contentEquals(packet(id = 2).aad()))
    }

    // ── Room encryption ─────────────────────────────────────────────

    @Test
    fun `same room code decrypts, other rooms cannot`() {
        val a = RoomCipher("convoy 1")
        val b = RoomCipher("  CONVOY 1 ")
        val other = RoomCipher("CONVOY 2")
        assertEquals(a.tag, b.tag)
        assertNotEquals(a.tag, other.tag)

        val aad = packet().aad()
        val sealed = a.seal("meet at the fuel stop".toByteArray(), aad)
        assertEquals("meet at the fuel stop", b.open(sealed, aad)?.toString(Charsets.UTF_8))
        assertNull(other.open(sealed, aad))
    }

    @Test
    fun `tampered payload or header fails to open`() {
        val c = RoomCipher("SQUAD ALPHA")
        val aad = packet().aad()
        val sealed = c.seal("sos".toByteArray(), aad)
        assertNull(c.open(sealed.clone().also { it[20] = (it[20] + 1).toByte() }, aad))
        assertNull(c.open(sealed, packet(id = 43).aad()))
    }

    // ── Routing ─────────────────────────────────────────────────────

    @Test
    fun `duplicates are handled once`() {
        val r = MeshRouter(now = { 1_000_000L })
        assertTrue(r.accept(packet()))
        assertFalse(r.accept(packet(ttl = 3)))      // same message through another path
        assertTrue(r.accept(packet(id = 43)))
        assertTrue(r.accept(packet(sender = 8)))    // same id, different sender
    }

    @Test
    fun `stale and future packets are rejected`() {
        val now = 10 * 60 * 60 * 1000L
        val r = MeshRouter(now = { now })
        assertFalse(r.accept(packet(ts = now - 7 * 60 * 60 * 1000L)))
        assertFalse(r.accept(packet(ts = now + 10 * 60 * 1000L)))
        assertTrue(r.accept(packet(ts = now - 60_000L)))
    }

    @Test
    fun `ttl decreases each hop and stops at one`() {
        val r = MeshRouter()
        assertEquals(6, r.relayCopy(packet(ttl = 7))?.ttl)
        assertEquals(1, r.relayCopy(packet(ttl = 2))?.ttl)
        assertNull(r.relayCopy(packet(ttl = 1)))
    }

    @Test
    fun `replay buffer keeps recent packets and drops old ones`() {
        var now = 1_000_000L
        val r = MeshRouter(now = { now }, maxAgeMs = 60_000L, bufferCapacity = 2)
        r.remember(packet(id = 1, ts = now))
        r.remember(packet(id = 2, ts = now))
        r.remember(packet(id = 3, ts = now))
        assertEquals(listOf(2L, 3L), r.replayable().map { it.messageId })
        now += 120_000L
        assertTrue(r.replayable().isEmpty())
    }

    @Test
    fun `stored message keys block history duplicates after restart`() {
        val r = MeshRouter(now = { 1_000_000L })
        r.markSeen(packet().key)
        assertFalse(r.accept(packet()))
    }

    // ── Profile & body encoding ─────────────────────────────────────

    @Test
    fun `profile round-trips with bike information`() {
        val p = RiderProfile(
            name = "Piyush",
            status = "Leading the pack",
            avatar = "🏍️",
            colorIndex = 5,
            bikeName = "Storm",
            bikeModel = "KTM Duke 390",
            bikeNickname = "Orange Beast",
            bikePlate = "MH-19-AB-1234"
        )
        val decoded = RiderProfile.decode(p.encode())
        assertEquals(p, decoded)
    }

    @Test
    fun `profile decodes legacy 4-field wire format safely`() {
        val legacy = "Piyush\u001fLeading\u001f🦅\u001f3".toByteArray(Charsets.UTF_8)
        val decoded = RiderProfile.decode(legacy)
        assertNotNull(decoded)
        assertEquals("Piyush", decoded!!.name)
        assertEquals("Leading", decoded.status)
        assertEquals("🦅", decoded.avatar)
        assertEquals(3, decoded.colorIndex)
        assertEquals("", decoded.bikeName)
        assertEquals("", decoded.bikeModel)
    }

    @Test
    fun `destination packet opcode round-trips`() {
        val p = packet().copy(type = MeshType.DESTINATION, payload = "SSBT College".toByteArray())
        val decoded = MeshPacket.decode(p.encode())
        assertEquals(MeshType.DESTINATION, decoded?.type)
        assertEquals("SSBT College", decoded?.payload?.toString(Charsets.UTF_8))
    }

    @Test
    fun `message body carries sender name and location extra`() {
        val raw = "12.97,77.59|Near SSBT College"
        val (name, body) = MessageBody.decode(MessageBody.encode("Piyush", raw))
        assertEquals("Piyush", name)
        assertEquals(12.97 to 77.59, MessageBody.parseLatLon(body))
        assertEquals("Near SSBT College", MessageBody.parseExtra(body))
    }


    // ── Photos ──────────────────────────────────────────────────────

    @Test
    fun `photo pieces round-trip and fit in one packet`() {
        val name = "Aayush"
        val data = ByteArray(ImageChunk.dataCapacity(name)) { it.toByte() }
        val chunk = ImageChunk(imageId = 77L, index = 3, total = 40, senderName = name, data = data)
        val encoded = chunk.encode()
        assertTrue(encoded.size <= RoomCipher.MAX_PLAINTEXT)
        val decoded = ImageChunk.decode(encoded)!!
        assertEquals(77L, decoded.imageId)
        assertEquals(3, decoded.index)
        assertEquals(40, decoded.total)
        assertEquals(name, decoded.senderName)
        assertArrayEquals(data, decoded.data)
    }

    @Test
    fun `a max-size photo needs at most the allowed number of pieces`() {
        val capacity = ImageChunk.dataCapacity("x".repeat(20))
        val pieces = (MAX_PHOTO_BYTES + capacity - 1) / capacity
        assertTrue(pieces <= ImageChunk.MAX_CHUNKS, "pieces=$pieces")
    }

    @Test
    fun `malformed photo pieces are rejected`() {
        assertNull(ImageChunk.decode(ByteArray(5)))
        val bad = ImageChunk(1, 5, 3, "a", ByteArray(4)).encode() // index >= total
        assertNull(ImageChunk.decode(bad))
    }

    // ── BIP340 (Nostr signatures) ───────────────────────────────────

    @Test
    fun `schnorr matches BIP340 test vector 0`() {
        val sk = hex("0000000000000000000000000000000000000000000000000000000000000003")
        val pk = Schnorr.publicKey(sk)
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", pk.toHex())
        val sig = Schnorr.sign(ByteArray(32), sk, ByteArray(32))
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            sig.toHex()
        )
        assertTrue(Schnorr.verify(ByteArray(32), pk, sig))
    }

    @Test
    fun `schnorr signatures verify and reject tampering`() {
        val sk = hex("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
        val msg = hex("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val sig = Schnorr.sign(msg, sk, ByteArray(32) { 1 })
        val pk = Schnorr.publicKey(sk)
        assertTrue(Schnorr.verify(msg, pk, sig))
        assertFalse(Schnorr.verify(msg.clone().also { it[0] = 0 }, pk, sig))
    }
}
