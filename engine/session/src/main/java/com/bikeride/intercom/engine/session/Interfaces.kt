package com.bikeride.intercom.engine.session

import com.bikeride.intercom.core.model.*
import kotlinx.coroutines.flow.StateFlow

/**
 * ConnectionManager — Section 20.2
 *
 * Owns all transports, drives the HandoverController, monitors link health,
 * and exposes the unified session state to the UI layer.
 *
 * Runs on the single-threaded control-plane dispatcher (Section 6.3)
 * for deterministic event ordering.
 */
interface ConnectionManager {
    /** Unified session state combining handover, audio, and Bluetooth states */
    val sessionState: StateFlow<SessionState>

    /** Start a session with a trusted peer */
    suspend fun start(peer: TrustedPeer, pref: ConnectionPreference)

    /** Stop the session and release all resources */
    suspend fun stop()

    /** Change the connection preference at runtime */
    fun setPreference(pref: ConnectionPreference)
}

/**
 * MediaEngine — Section 20.2
 *
 * Owns the entire audio pipeline:
 * TX: Mic → APM → Opus → packetize → encrypt → fan-out to transports
 * RX: transports → decrypt → dedup → jitter buffer → decode → mixer → playout
 */
interface MediaEngine {
    /** Start audio capture and playout */
    fun start()

    /** Stop audio capture and playout */
    fun stop()

    /** Mute/unmute the local microphone */
    fun setMuted(muted: Boolean)

    /** Attach a transport for sending/receiving packets */
    fun attach(transport: com.bikeride.intercom.transport.api.AudioTransport)

    /** Detach a transport */
    fun detach(transport: com.bikeride.intercom.transport.api.AudioTransport)

    /** Live audio stats for diagnostics */
    val stats: StateFlow<AudioStats>
}

/**
 * BluetoothRouteManager — Section 20.2
 *
 * Manages audio routing to/from Bluetooth headsets (HFP/SCO, LE Audio).
 */
interface BluetoothRouteManager {
    /** Current audio routing state */
    val route: StateFlow<AudioRouteState>

    /** Acquire the Bluetooth audio route (start SCO, set communication device) */
    suspend fun acquire()

    /** Release the Bluetooth audio route */
    suspend fun release()
}

/**
 * DuckingController — Section 20.2
 *
 * Manages music ducking when remote speech is active.
 */
interface DuckingController {
    /** Called when remote speech starts/stops (from VAD) */
    fun onRemoteSpeech(active: Boolean)

    /** Set the ducking level */
    fun setLevel(level: DuckingLevel)
}

/**
 * PairingManager — Section 20.2
 *
 * Handles the pairing flow: create offer (QR/code), join, handshake.
 */
interface PairingManager {
    /** Create a pairing offer (QR code + short code) */
    suspend fun createOffer(): PairingOffer

    /** Join a connection using a received payload */
    suspend fun join(payload: PairingPayload): PairingResult
}

/**
 * SecureChannel — Section 20.2
 *
 * Handles AEAD encryption/decryption of audio packets.
 * Same key protects packets on both Local and Internet transports,
 * so handover doesn't require renegotiation.
 */
interface SecureChannel {
    /** Encrypt an audio frame → AudioPacket with AEAD ciphertext */
    fun seal(header: ByteArray, plaintext: ByteArray): AudioPacket

    /** Decrypt an AudioPacket → plaintext, or null if authentication fails */
    fun open(packet: AudioPacket): ByteArray?
}
