package com.bikeride.intercom.mesh

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Convoy-wide encryption. Every rider who knows the room code derives the same AES-256 key,
 * so riders from other rooms can relay our packets but cannot read them.
 *
 * This is a shared room key, not per-person end-to-end encryption: anyone who knows the
 * room code can read that room. Pick an uncommon room code for private rides.
 */
class RoomCipher(roomCode: String) {

    val room: String = normalize(roomCode)

    /** 4-byte public room identifier placed in every packet header. */
    val tag: Int = ByteBuffer.wrap(sha256("astraride-room-tag:$room")).int

    /** Longer public identifier used as the Nostr subscription tag. */
    val internetTag: String = "astraride-" + sha256("astraride-nostr:$room").take(10).joinToString("") { "%02x".format(it) }

    private val key = SecretKeySpec(deriveKey(room), "AES")
    private val random = SecureRandom()

    fun seal(plain: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plain)
    }

    /** Returns null when the packet was not made with this room's key or was tampered with. */
    fun open(sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (sealed.size < NONCE_SIZE + TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_SIZE))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, NONCE_SIZE, sealed.size - NONCE_SIZE)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        const val OVERHEAD = NONCE_SIZE + TAG_BITS / 8

        /** Biggest plaintext that still fits in one packet. */
        const val MAX_PLAINTEXT = MeshPacket.MAX_PACKET_SIZE - MeshPacket.HEADER_SIZE - OVERHEAD

        fun normalize(roomCode: String) = roomCode.trim().uppercase()

        private fun deriveKey(room: String): ByteArray {
            val spec = PBEKeySpec(room.toCharArray(), "AstraRide-ConvoyMesh-v1".toByteArray(), 20_000, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    }
}
