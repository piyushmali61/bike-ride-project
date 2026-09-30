package com.bikeride.intercom.mesh

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Minimal BIP340 Schnorr signatures over secp256k1 — just what Nostr needs to sign events.
 * Affine arithmetic with BigInteger: slow (a few ms per signature) but dependency-free.
 */
object Schnorr {
    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val G = Point(
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    )
    private val SEVEN = BigInteger.valueOf(7)
    private val TWO = BigInteger.TWO
    private val THREE = BigInteger.valueOf(3)

    private data class Point(val x: BigInteger, val y: BigInteger)

    private fun add(a: Point?, b: Point?): Point? {
        if (a == null) return b
        if (b == null) return a
        if (a.x == b.x && a.y != b.y) return null
        val lambda = if (a == b) {
            THREE * a.x * a.x * (TWO * a.y).modInverse(P)
        } else {
            (b.y - a.y) * (b.x - a.x).modInverse(P)
        }.mod(P)
        val x = (lambda * lambda - a.x - b.x).mod(P)
        val y = (lambda * (a.x - x) - a.y).mod(P)
        return Point(x, y)
    }

    private fun mul(point: Point, k: BigInteger): Point? {
        var result: Point? = null
        var addend: Point? = point
        for (i in 0 until k.bitLength()) {
            if (k.testBit(i)) result = add(result, addend)
            addend = add(addend, addend)
        }
        return result
    }

    private fun liftX(x: BigInteger): Point? {
        if (x >= P) return null
        val c = (x.modPow(THREE, P) + SEVEN).mod(P)
        val y = c.modPow((P + BigInteger.ONE).shiftRight(2), P)
        if (y.modPow(TWO, P) != c) return null
        return Point(x, if (y.testBit(0)) P - y else y)
    }

    private fun bytes32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun int(b: ByteArray) = BigInteger(1, b)

    private fun taggedHash(tag: String, vararg parts: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        val tagHash = sha.digest(tag.toByteArray())
        sha.reset()
        sha.update(tagHash)
        sha.update(tagHash)
        parts.forEach { sha.update(it) }
        return sha.digest()
    }

    /** 32-byte x-only public key for a 32-byte secret key. */
    fun publicKey(secretKey: ByteArray): ByteArray {
        val d = int(secretKey)
        require(d > BigInteger.ZERO && d < N) { "invalid secret key" }
        return bytes32(mul(G, d)!!.x)
    }

    fun sign(message: ByteArray, secretKey: ByteArray, auxRand: ByteArray): ByteArray {
        val d0 = int(secretKey)
        require(d0 > BigInteger.ZERO && d0 < N) { "invalid secret key" }
        val p = mul(G, d0)!!
        val d = if (p.y.testBit(0)) N - d0 else d0
        val t = bytes32(d).zip(taggedHash("BIP0340/aux", auxRand)) { a, b -> (a.toInt() xor b.toInt()).toByte() }.toByteArray()
        val k0 = int(taggedHash("BIP0340/nonce", t, bytes32(p.x), message)).mod(N)
        require(k0 != BigInteger.ZERO) { "bad nonce" }
        val r = mul(G, k0)!!
        val k = if (r.y.testBit(0)) N - k0 else k0
        val e = int(taggedHash("BIP0340/challenge", bytes32(r.x), bytes32(p.x), message)).mod(N)
        return bytes32(r.x) + bytes32((k + e * d).mod(N))
    }

    fun verify(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        val p = liftX(int(publicKey)) ?: return false
        val r = int(signature.copyOfRange(0, 32))
        val s = int(signature.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = int(taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(N)
        val rPoint = add(mul(G, s), mul(p, N - e)) ?: return false
        return !rPoint.y.testBit(0) && rPoint.x == r
    }
}
