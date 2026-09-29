package com.bikeride.intercom.transport.local.nearby

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

enum class MeshConnectionState {
    IDLE,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

/**
 * High-performance off-grid peer-to-peer Wi-Fi Direct and Bluetooth mesh transport.
 * Features 1-Click zero-configuration pairing: both riders tap "Ride / Connect"
 * and are connected within 1-2 seconds with zero internet required.
 */
@Singleton
class NearbyMeshTransport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SERVICE_ID = "com.bikeride.intercom.mesh"
        val STRATEGY = Strategy.P2P_CLUSTER

        // Packet headers
        const val PKT_AUDIO: Byte = 0x01
        const val PKT_PING: Byte = 0x02
        const val PKT_PONG: Byte = 0x03
        const val PKT_HORN: Byte = 0x04
        const val PKT_MUTE: Byte = 0x05
    }

    private val client = Nearby.getConnectionsClient(context)

    private val _state = MutableStateFlow(MeshConnectionState.IDLE)
    val state: StateFlow<MeshConnectionState> = _state.asStateFlow()

    private val _connectedPeerName = MutableStateFlow<String?>(null)
    val connectedPeerName: StateFlow<String?> = _connectedPeerName.asStateFlow()

    private val _latencyMs = MutableStateFlow(18L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _peerMuted = MutableStateFlow(false)
    val peerMuted: StateFlow<Boolean> = _peerMuted.asStateFlow()

    private val _emergencyAlert = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val emergencyAlert: SharedFlow<Unit> = _emergencyAlert.asSharedFlow()

    private var activeEndpointId: String? = null
    private var pingJob: Job? = null
    private var searchTimeoutJob: Job? = null

    var onAudioFrameReceived: ((ByteArray) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.isEmpty()) return

            when (bytes[0]) {
                PKT_AUDIO -> {
                    if (bytes.size > 1) {
                        val audioData = bytes.copyOfRange(1, bytes.size)
                        onAudioFrameReceived?.invoke(audioData)
                    }
                }
                PKT_PING -> {
                    // Send pong back immediately
                    if (bytes.size >= 9) {
                        val buffer = ByteBuffer.wrap(bytes, 1, 8)
                        val timestamp = buffer.long
                        sendPong(endpointId, timestamp)
                    }
                }
                PKT_PONG -> {
                    if (bytes.size >= 9) {
                        val buffer = ByteBuffer.wrap(bytes, 1, 8)
                        val originalTime = buffer.long
                        val rtt = SystemClock.elapsedRealtime() - originalTime
                        if (rtt in 1..2000) {
                            _latencyMs.value = rtt
                        }
                    }
                }
                PKT_HORN -> {
                    Timber.w("Emergency horn packet received from peer!")
                    _emergencyAlert.tryEmit(Unit)
                }
                PKT_MUTE -> {
                    if (bytes.size > 1) {
                        _peerMuted.value = (bytes[1] == 1.toByte())
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Timber.i("Connection initiated with ${info.endpointName} ($endpointId)")
            _state.value = MeshConnectionState.CONNECTING
            _connectedPeerName.value = info.endpointName
            // Auto accept for 1-click zero hassle rider experience
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                Timber.i("Connected successfully to $endpointId")
                activeEndpointId = endpointId
                _state.value = MeshConnectionState.CONNECTED
                stopAdvertisingAndDiscovery()
            } else {
                Timber.w("Connection to $endpointId failed: ${resolution.status.statusCode}")
                _state.value = MeshConnectionState.DISCONNECTED
                activeEndpointId = null
            }
        }

        override fun onDisconnected(endpointId: String) {
            Timber.i("Disconnected from $endpointId")
            if (activeEndpointId == endpointId) {
                activeEndpointId = null
                _state.value = MeshConnectionState.DISCONNECTED
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Timber.i("Discovered peer: ${info.endpointName} ($endpointId)")
            if (_state.value == MeshConnectionState.SEARCHING) {
                _state.value = MeshConnectionState.CONNECTING
                val myName = "${Build.MANUFACTURER} ${Build.MODEL}"
                client.requestConnection(myName, endpointId, connectionLifecycleCallback)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint lost: $endpointId")
        }
    }

    fun startOneClickMesh(scope: CoroutineScope, customRideCode: String? = null) {
        disconnect()
        _state.value = MeshConnectionState.SEARCHING
        val deviceName = customRideCode?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}"

        // Start Advertising
        val advOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(deviceName, SERVICE_ID, connectionLifecycleCallback, advOptions)
            .addOnSuccessListener { Timber.i("Mesh advertising started as $deviceName") }
            .addOnFailureListener { e -> Timber.e(e, "Mesh advertising failed") }

        // Start Discovery simultaneously
        val discOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discOptions)
            .addOnSuccessListener { Timber.i("Mesh discovery started") }
            .addOnFailureListener { e -> Timber.e(e, "Mesh discovery failed") }

        // Auto-cancel search after 90 seconds if no peer found to prevent battery drain
        searchTimeoutJob?.cancel()
        searchTimeoutJob = scope.launch {
            delay(90_000L)
            if (_state.value == MeshConnectionState.SEARCHING) {
                Timber.w("Mesh search timed out after 90s to conserve battery")
                disconnect()
            }
        }

        // Start periodic Ping job to measure latency (every 2.5s to minimize battery wakeups)
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2500)
                if (_state.value == MeshConnectionState.CONNECTED && activeEndpointId != null) {
                    sendPing()
                }
            }
        }
    }

    fun sendAudioFrame(frame: ByteArray) {
        val endpoint = activeEndpointId ?: return
        if (_state.value != MeshConnectionState.CONNECTED) return

        val packet = ByteArray(frame.size + 1)
        packet[0] = PKT_AUDIO
        System.arraycopy(frame, 0, packet, 1, frame.size)
        client.sendPayload(endpoint, Payload.fromBytes(packet))
    }

    fun sendEmergencyHornAlert() {
        val endpoint = activeEndpointId ?: return
        val packet = byteArrayOf(PKT_HORN)
        client.sendPayload(endpoint, Payload.fromBytes(packet))
    }

    fun sendMuteState(isMuted: Boolean) {
        val endpoint = activeEndpointId ?: return
        val packet = byteArrayOf(PKT_MUTE, if (isMuted) 1 else 0)
        client.sendPayload(endpoint, Payload.fromBytes(packet))
    }

    private fun sendPing() {
        val endpoint = activeEndpointId ?: return
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PKT_PING)
        buffer.putLong(SystemClock.elapsedRealtime())
        client.sendPayload(endpoint, Payload.fromBytes(buffer.array()))
    }

    private fun sendPong(endpoint: String, timestamp: Long) {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PKT_PONG)
        buffer.putLong(timestamp)
        client.sendPayload(endpoint, Payload.fromBytes(buffer.array()))
    }

    private fun stopAdvertisingAndDiscovery() {
        try {
            client.stopAdvertising()
            client.stopDiscovery()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping advertising/discovery")
        }
    }

    fun disconnect() {
        searchTimeoutJob?.cancel()
        searchTimeoutJob = null
        pingJob?.cancel()
        pingJob = null
        stopAdvertisingAndDiscovery()
        try {
            activeEndpointId?.let { client.disconnectFromEndpoint(it) }
            client.stopAllEndpoints()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting NearbyMeshTransport")
        }
        activeEndpointId = null
        _state.value = MeshConnectionState.IDLE
        _connectedPeerName.value = null
    }
}
