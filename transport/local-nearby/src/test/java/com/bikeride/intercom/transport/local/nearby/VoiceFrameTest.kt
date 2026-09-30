package com.bikeride.intercom.transport.local.nearby

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VoiceFrameTest {

    private val pcm = ByteArray(640) { it.toByte() }

    @Test
    fun `frame round-trips`() {
        val f = VoiceFrame(senderId = -123456, seq = 65535, ttl = 4, pcm = pcm)
        assertEquals(f, VoiceFrame.decode(f.encode(), legacySenderId = 0, pcmSize = 640))
    }

    @Test
    fun `bare pcm from older versions is accepted but not relayable`() {
        val f = VoiceFrame.decode(pcm, legacySenderId = 99, pcmSize = 640)!!
        assertEquals(99, f.senderId)
        assertEquals(1, f.ttl)
    }

    @Test
    fun `wrong sizes are rejected`() {
        assertNull(VoiceFrame.decode(ByteArray(100), 0, 640))
    }

    @Test
    fun `same frame over two links plays once`() {
        val d = VoiceDedup()
        val f = VoiceFrame(1, 10, 4, pcm)
        assertTrue(d.firstTime(f))
        assertFalse(d.firstTime(f.copy(ttl = 3)))   // relayed copy
        assertTrue(d.firstTime(f.copy(seq = 11)))
        assertTrue(d.firstTime(f.copy(senderId = 2)))
    }

    @Test
    fun `window forgets old sequence numbers so wrap-around works`() {
        val d = VoiceDedup(window = 4)
        (0 until 6).forEach { assertTrue(d.firstTime(VoiceFrame(1, it, 4, pcm))) }
        assertTrue(d.firstTime(VoiceFrame(1, 0, 4, pcm)))
    }
}
