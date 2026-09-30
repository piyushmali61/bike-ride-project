package com.bikeride.intercom.transport.local.nearby

import android.content.Context
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class LanPeer(
    val address: InetAddress,
    val port: Int,
    val riderId: String,
    val displayName: String,
    val isMuted: Boolean = false,
    val lastSeen: Long = SystemClock.elapsedRealtime()
)

/**
 * Ultra-low-latency (< 3ms) local Wi-Fi & Mobile Hotspot UDP transport.
 * Functions exactly like a Local Call App: devices connected to the same personal hotspot
 * or Wi-Fi network instantly discover each other and stream full-duplex audio without delay.
 */
@Singleton
class LocalLanTransport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val UDP_PORT = 50005
        const val BEACON_HEADER = "ASTRA_BEACON"
        const val PKT_AUDIO: Byte = 0x01
        const val PKT_MUTE: Byte = 0x05
        const val PKT_HORN: Byte = 0x04
    }

    val myRiderId: String = UUID.randomUUID().toString().take(6).uppercase()

    private val _connectedLanPeers = MutableStateFlow<Map<String, LanPeer>>(emptyMap())
    val connectedLanPeers: StateFlow<Map<String, LanPeer>> = _connectedLanPeers.asStateFlow()

    private val peerLastSeen = ConcurrentHashMap<String, Long>()
    private var cachedLocalAddresses: Set<InetAddress> = emptySet()
    private var lastAddressCacheTime = 0L

    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var beaconJob: Job? = null
    private var isRunning = false
    private var currentRoom = "CONVOY 1"

    var onAudioFrameReceived: ((ByteArray) -> Unit)? = null
    var onPeerMuteChanged: ((String, Boolean) -> Unit)? = null
    var onEmergencyHornReceived: (() -> Unit)? = null

    fun start(scope: CoroutineScope, roomName: String) {
        if (isRunning) stop()
        currentRoom = roomName.trim().uppercase()
        isRunning = true

        try {
            val s = DatagramSocket(null)
            s.reuseAddress = true
            s.broadcast = true
            s.receiveBufferSize = 65536
            s.sendBufferSize = 65536
            s.bind(InetSocketAddress(UDP_PORT))
            socket = s
            Timber.i("LocalLanTransport UDP socket started on port $UDP_PORT for room [$currentRoom]")
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind UDP socket on port $UDP_PORT, retrying with wildcard port")
            try {
                socket = DatagramSocket().apply { broadcast = true }
            } catch (ex: Exception) {
                Timber.e(ex, "Failed to create fallback UDP socket")
                return
            }
        }

        // Listener loop
        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isActive && isRunning) {
                try {
                    val s = socket ?: break
                    // CRITICAL: Always reset length before receive to prevent buffer shrinking
                    packet.length = buffer.size
                    s.receive(packet)
                    val len = packet.length
                    if (len <= 0) continue

                    val data = packet.data
                    val senderAddress = packet.address
                    val senderPort = packet.port

                    // Ignore own packets
                    if (isLocalIpAddress(senderAddress)) {
                        continue
                    }

                    // Check if packet is a Discovery Beacon
                    if (len > BEACON_HEADER.length && String(data, 0, minOf(len, 20)).startsWith(BEACON_HEADER)) {
                        val beaconStr = String(data, 0, len)
                        parseBeacon(beaconStr, senderAddress, senderPort)
                    } else if (data[0] == PKT_AUDIO && len > 1) {
                        val key = senderAddress.hostAddress ?: "$senderAddress"
                        peerLastSeen[key] = SystemClock.elapsedRealtime()
                        val audioData = data.copyOfRange(1, len)
                        onAudioFrameReceived?.invoke(audioData)
                    } else if (data[0] == PKT_MUTE && len > 1) {
                        val key = senderAddress.hostAddress ?: "$senderAddress"
                        peerLastSeen[key] = SystemClock.elapsedRealtime()
                        val muted = (data[1] == 1.toByte())
                        updatePeerMute(senderAddress.hostAddress ?: "", muted)
                    } else if (data[0] == PKT_HORN) {
                        val key = senderAddress.hostAddress ?: "$senderAddress"
                        peerLastSeen[key] = SystemClock.elapsedRealtime()
                        onEmergencyHornReceived?.invoke()
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Timber.d(e, "UDP receive packet error")
                    }
                }
            }
        }

        // Periodic Broadcast & Unicast Beacon (every 1200ms)
        beaconJob = scope.launch(Dispatchers.IO) {
            val myDevice = "Rider $myRiderId"

            while (isActive && isRunning) {
                val broadcastAddresses = getBroadcastAddresses()
                val beaconMessage = "$BEACON_HEADER|$currentRoom|$myRiderId|$myDevice"
                val beaconBytes = beaconMessage.toByteArray()

                // 1. Send broadcast beacons
                for (bcast in broadcastAddresses) {
                    try {
                        val beaconPacket = DatagramPacket(beaconBytes, beaconBytes.size, bcast, UDP_PORT)
                        socket?.send(beaconPacket)
                    } catch (e: Exception) {
                        // ignore network switch exceptions
                    }
                }

                // 2. Direct Unicast keepalive to each known peer (immune to Wi-Fi broadcast power-saving)
                val peers = _connectedLanPeers.value.values
                for (peer in peers) {
                    try {
                        val unicastPacket = DatagramPacket(beaconBytes, beaconBytes.size, peer.address, peer.port)
                        socket?.send(unicastPacket)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                // Prune dead peers only after 25 seconds of sustained inactivity
                val now = SystemClock.elapsedRealtime()
                val current = _connectedLanPeers.value
                val active = current.filter { (key, _) ->
                    val last = peerLastSeen[key] ?: 0L
                    now - last < 25000L
                }
                if (active.size != current.size) {
                    _connectedLanPeers.value = active
                    peerLastSeen.keys.retainAll(active.keys)
                }

                delay(1200)
            }
        }
    }

    private fun parseBeacon(beaconStr: String, senderAddress: InetAddress, senderPort: Int) {
        val parts = beaconStr.split("|")
        if (parts.size >= 4) {
            val room = parts[1]
            val peerRiderId = parts[2]
            val peerModel = parts[3]

            if (peerRiderId == myRiderId) return // self
            if (!room.equals(currentRoom, ignoreCase = true)) return // different room

            val key = senderAddress.hostAddress ?: "$senderAddress"
            peerLastSeen[key] = SystemClock.elapsedRealtime()

            val existing = _connectedLanPeers.value[key]
            if (existing == null || existing.riderId != peerRiderId || existing.displayName != peerModel) {
                val peer = LanPeer(
                    address = senderAddress,
                    port = UDP_PORT,
                    riderId = peerRiderId,
                    displayName = peerModel,
                    isMuted = existing?.isMuted ?: false,
                    lastSeen = SystemClock.elapsedRealtime()
                )

                val updated = _connectedLanPeers.value.toMutableMap()
                updated[key] = peer
                _connectedLanPeers.value = updated

                Timber.i("LAN Room Peer Discovered via Hotspot/Wi-Fi: $peerModel ($key)")
            }
        }
    }

    private fun updatePeerMute(host: String, isMuted: Boolean) {
        val current = _connectedLanPeers.value.toMutableMap()
        val peer = current[host] ?: return
        current[host] = peer.copy(isMuted = isMuted)
        _connectedLanPeers.value = current
        onPeerMuteChanged?.invoke(peer.displayName, isMuted)
    }

    fun sendAudioFrame(frame: ByteArray) {
        val peers = _connectedLanPeers.value.values
        if (peers.isEmpty() || !isRunning) return

        val packetData = ByteArray(frame.size + 1)
        packetData[0] = PKT_AUDIO
        System.arraycopy(frame, 0, packetData, 1, frame.size)

        for (peer in peers) {
            try {
                val packet = DatagramPacket(packetData, packetData.size, peer.address, peer.port)
                socket?.send(packet)
            } catch (e: Exception) {
                Timber.d(e, "Error sending UDP audio frame to ${peer.address}")
            }
        }
    }

    fun sendMuteState(isMuted: Boolean) {
        val peers = _connectedLanPeers.value.values
        if (peers.isEmpty() || !isRunning) return
        val packetData = byteArrayOf(PKT_MUTE, if (isMuted) 1 else 0)
        for (peer in peers) {
            try {
                val packet = DatagramPacket(packetData, packetData.size, peer.address, peer.port)
                socket?.send(packet)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun sendEmergencyHornAlert() {
        val peers = _connectedLanPeers.value.values
        if (peers.isEmpty() || !isRunning) return
        val packetData = byteArrayOf(PKT_HORN)
        for (peer in peers) {
            try {
                val packet = DatagramPacket(packetData, packetData.size, peer.address, peer.port)
                socket?.send(packet)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val bcast = interfaceAddress.broadcast
                    if (bcast != null) {
                        list.add(bcast)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Error gathering broadcast addresses")
        }
        return list.distinct()
    }

    private fun isLocalIpAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastAddressCacheTime > 10000L || cachedLocalAddresses.isEmpty()) {
            try {
                val set = mutableSetOf<InetAddress>()
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    for (interfaceAddress in iface.interfaceAddresses) {
                        val a = interfaceAddress.address
                        if (a != null) {
                            set.add(a)
                        }
                    }
                }
                cachedLocalAddresses = set
                lastAddressCacheTime = now
            } catch (e: Exception) {
                // ignore
            }
        }
        return cachedLocalAddresses.contains(addr)
    }

    fun stop() {
        isRunning = false
        receiveJob?.cancel()
        receiveJob = null
        beaconJob?.cancel()
        beaconJob = null
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        peerLastSeen.clear()
        cachedLocalAddresses = emptySet()
        _connectedLanPeers.value = emptyMap()
    }
}
