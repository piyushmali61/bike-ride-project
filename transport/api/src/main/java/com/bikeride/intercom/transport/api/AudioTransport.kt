package com.bikeride.intercom.transport.api

import com.bikeride.intercom.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport abstraction — Section 20.1
 *
 * Each implementation carries the same encrypted [AudioPacket]s
 * and reliable [ControlMessage]s. The receiver never needs to know
 * which transport delivered a packet — handover is simply changing
 * which transport(s) the fan-out uses.
 *
 * Implementations:
 * - [LocalNearbyTransport] — Nearby Connections (v1 primary)
 * - [InternetWebRtcTransport] — WebRTC DataChannel
 * - [LocalWifiDirectTransport] — Wi-Fi Direct (contingent)
 * - BluetoothFallbackTransport — disabled stub
 */
interface AudioTransport {

    /** Which transport this is */
    val kind: TransportKind

    /** Current connection state */
    val state: StateFlow<TransportState>

    /** Latest link quality (updated at ~1 Hz by internal monitoring) */
    val quality: StateFlow<LinkQuality>

    /**
     * Incoming audio packets, already integrity-checked at the packet level.
     * Decryption and dedup happen at the MediaEngine layer.
     */
    val incoming: Flow<AudioPacket>

    /** Incoming reliable control messages */
    val controlIncoming: Flow<ControlMessage>

    /**
     * Connect to a known peer.
     * Returns when the transport-level connection is established
     * (app-level authentication is a separate step).
     */
    suspend fun connect(peer: TrustedPeer, hints: ConnectHints)

    /**
     * Disconnect from the peer.
     */
    suspend fun disconnect(reason: DisconnectReason)

    /**
     * NON-BLOCKING send of an audio packet.
     * Bounded queue (~3 frames / 60ms). If full, drops OLDEST (never blocks).
     * Returns the result indicating what happened.
     */
    fun trySend(packet: AudioPacket): SendResult

    /**
     * Send a reliable control message (ordered, guaranteed delivery).
     */
    suspend fun sendControl(msg: ControlMessage)

    /**
     * Active RTT probe (ping/pong).
     */
    suspend fun probe(): ProbeResult
}
