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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class MeshConnectionState {
    IDLE,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

data class ConnectedRider(
    val endpointId: String,
    val name: String,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val lastSeen: Long = SystemClock.elapsedRealtime()
)

/**
 * High-performance off-grid multi-biker mesh transport utilizing Google Nearby Connections
 * with Strategy.P2P_CLUSTER.
 * Supports 2, 3, 4, 8+ motorcyclists in the same Room/Convoy communicating simultaneously
 * in crystal-clear full duplex with zero internet.
 */
@Singleton
class NearbyMeshTransport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val BASE_SERVICE_ID = "com.bikeride.intercom.room."
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

    private val _currentRoom = MutableStateFlow("CONVOY 1")
    val currentRoom: StateFlow<String> = _currentRoom.asStateFlow()

    // Multi-rider connected roster: endpointId -> ConnectedRider
    private val _connectedRiders = MutableStateFlow<Map<String, ConnectedRider>>(emptyMap())
    val connectedRiders: StateFlow<Map<String, ConnectedRider>> = _connectedRiders.asStateFlow()

    // Backward-compatible single peer name (e.g. for simple labels)
    val connectedPeerName: StateFlow<String?> = _connectedRiders.map { riders ->
        when (riders.size) {
            0 -> null
            1 -> riders.values.first().name
            else -> "${riders.values.first().name} + ${riders.size - 1} riders"
        }
    }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, null)

    private val _latencyMs = MutableStateFlow(18L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _peerMuted = MutableStateFlow(false)
    val peerMuted: StateFlow<Boolean> = _peerMuted.asStateFlow()

    private val _emergencyAlert = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val emergencyAlert: SharedFlow<Unit> = _emergencyAlert.asSharedFlow()

    private var pingJob: Job? = null
    private var isAdvertisingOrDiscovering = false
    private val pendingEndpoints = ConcurrentHashMap.newKeySet<String>()

    var onAudioFrameReceived: ((ByteArray) -> Unit)? = null

    private fun sanitizeRoomServiceId(room: String): String {
        val clean = room.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        return BASE_SERVICE_ID + (if (clean.isBlank()) "convoy1" else clean)
    }

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
                        // Mark rider as actively speaking
                        updateRiderSpeaking(endpointId, true)
                    }
                }
                PKT_PING -> {
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
                    Timber.w("Emergency horn alert packet received from $endpointId!")
                    _emergencyAlert.tryEmit(Unit)
                }
                PKT_MUTE -> {
                    if (bytes.size > 1) {
                        val isMuted = (bytes[1] == 1.toByte())
                        updateRiderMute(endpointId, isMuted)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Timber.i("Room connection initiated with ${info.endpointName} ($endpointId)")
            if (_state.value != MeshConnectionState.CONNECTED) {
                _state.value = MeshConnectionState.CONNECTING
            }
            // Auto accept connection in cluster
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (resolution.status.isSuccess) {
                Timber.i("Rider successfully linked to room mesh: $endpointId")
                val riderName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val current = _connectedRiders.value.toMutableMap()
                current[endpointId] = ConnectedRider(
                    endpointId = endpointId,
                    name = riderName
                )
                _connectedRiders.value = current
                _state.value = MeshConnectionState.CONNECTED
                // In cluster mesh, keep advertising & discovery running so other bikers can join!
            } else {
                Timber.w("Rider connection failed to $endpointId: ${resolution.status.statusCode}")
                if (_connectedRiders.value.isEmpty()) {
                    _state.value = MeshConnectionState.SEARCHING
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Timber.i("Rider disconnected: $endpointId")
            val current = _connectedRiders.value.toMutableMap()
            current.remove(endpointId)
            _connectedRiders.value = current
            if (current.isEmpty()) {
                _state.value = MeshConnectionState.SEARCHING
                _peerMuted.value = false
            } else {
                _state.value = MeshConnectionState.CONNECTED
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Timber.i("Discovered room rider: ${info.endpointName} ($endpointId)")
            if (!_connectedRiders.value.containsKey(endpointId) && !pendingEndpoints.contains(endpointId)) {
                pendingEndpoints.add(endpointId)
                val myName = "${Build.MANUFACTURER} ${Build.MODEL}"
                client.requestConnection(myName, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener {
                        pendingEndpoints.remove(endpointId)
                    }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Timber.d("Endpoint lost: $endpointId")
            pendingEndpoints.remove(endpointId)
        }
    }

    /**
     * Starts or switches to a multi-biker Room Mesh.
     * All riders using the same room code (e.g. "CONVOY 1", "SQUAD ALPHA") will automatically
     * cluster into full-duplex intercom.
     */
    fun startOneClickMesh(scope: CoroutineScope, roomCode: String? = null) {
        val targetRoom = roomCode?.takeIf { it.isNotBlank() } ?: _currentRoom.value
        _currentRoom.value = targetRoom
        val serviceId = sanitizeRoomServiceId(targetRoom)

        disconnect()
        _state.value = MeshConnectionState.SEARCHING
        val myDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val advOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(myDeviceName, serviceId, connectionLifecycleCallback, advOptions)
            .addOnSuccessListener {
                isAdvertisingOrDiscovering = true
                Timber.i("Room mesh advertising started for [$targetRoom] ($serviceId)")
            }
            .addOnFailureListener { e -> Timber.e(e, "Room mesh advertising failed") }

        val discOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(serviceId, endpointDiscoveryCallback, discOptions)
            .addOnSuccessListener {
                isAdvertisingOrDiscovering = true
                Timber.i("Room mesh discovery started for [$targetRoom] ($serviceId)")
            }
            .addOnFailureListener { e -> Timber.e(e, "Room mesh discovery failed") }

        // Periodic ping to all riders to measure network latency
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                if (_connectedRiders.value.isNotEmpty()) {
                    sendPingToAll()
                }
            }
        }
    }

    fun sendAudioFrame(frame: ByteArray) {
        val endpoints = _connectedRiders.value.keys.toList()
        if (endpoints.isEmpty() || _state.value != MeshConnectionState.CONNECTED) return

        val packet = ByteArray(frame.size + 1)
        packet[0] = PKT_AUDIO
        System.arraycopy(frame, 0, packet, 1, frame.size)

        // Send to all connected riders in the room
        client.sendPayload(endpoints, Payload.fromBytes(packet))
    }

    fun sendEmergencyHornAlert() {
        val endpoints = _connectedRiders.value.keys.toList()
        if (endpoints.isEmpty()) return
        val packet = byteArrayOf(PKT_HORN)
        client.sendPayload(endpoints, Payload.fromBytes(packet))
    }

    fun sendMuteState(isMuted: Boolean) {
        val endpoints = _connectedRiders.value.keys.toList()
        if (endpoints.isEmpty()) return
        val packet = byteArrayOf(PKT_MUTE, if (isMuted) 1 else 0)
        client.sendPayload(endpoints, Payload.fromBytes(packet))
    }

    private fun sendPingToAll() {
        val endpoints = _connectedRiders.value.keys.toList()
        if (endpoints.isEmpty()) return
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PKT_PING)
        buffer.putLong(SystemClock.elapsedRealtime())
        client.sendPayload(endpoints, Payload.fromBytes(buffer.array()))
    }

    private fun sendPong(endpoint: String, timestamp: Long) {
        val buffer = ByteBuffer.allocate(9)
        buffer.put(PKT_PONG)
        buffer.putLong(timestamp)
        client.sendPayload(endpoint, Payload.fromBytes(buffer.array()))
    }

    private fun updateRiderSpeaking(endpointId: String, isSpeaking: Boolean) {
        val rider = _connectedRiders.value[endpointId] ?: return
        val current = _connectedRiders.value.toMutableMap()
        current[endpointId] = rider.copy(isSpeaking = isSpeaking, lastSeen = SystemClock.elapsedRealtime())
        _connectedRiders.value = current
    }

    private fun updateRiderMute(endpointId: String, isMuted: Boolean) {
        val rider = _connectedRiders.value[endpointId] ?: return
        val current = _connectedRiders.value.toMutableMap()
        current[endpointId] = rider.copy(isMuted = isMuted)
        _connectedRiders.value = current
        _peerMuted.value = current.values.any { it.isMuted }
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        try {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room mesh")
        }
        isAdvertisingOrDiscovering = false
        pendingEndpoints.clear()
        _connectedRiders.value = emptyMap()
        _state.value = MeshConnectionState.IDLE
    }
}
