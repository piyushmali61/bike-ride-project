package com.bikeride.intercom.spikes.ui.spikes

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.spikes.measurement.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Spike A: Nearby Connections Range & Transport Characteristics
 *
 * Tests discovery time, sustained range, RTT, throughput, HOL blocking,
 * and BYTES vs STREAM payload comparison.
 *
 * Install on BOTH phones. One taps "Advertise", the other taps "Discover".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeANearbyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val collector = remember { MeasurementCollector("SpikeA-Nearby") }
    val latencyTracker = remember { LatencyTracker() }
    val throughputTracker = remember { ThroughputTracker() }

    var role by remember { mutableStateOf<String?>(null) } // "advertiser" or "discoverer"
    var connectionState by remember { mutableStateOf("Idle") }
    var connectedEndpointId by remember { mutableStateOf<String?>(null) }
    var pingCount by remember { mutableIntStateOf(0) }
    var lastRtt by remember { mutableStateOf<Long?>(null) }
    var avgRtt by remember { mutableStateOf<Double?>(null) }
    var throughputBps by remember { mutableDoubleStateOf(0.0) }
    var packetsReceived by remember { mutableIntStateOf(0) }
    var testRunning by remember { mutableStateOf(false) }
    var pingJob by remember { mutableStateOf<Job?>(null) }

    val logLines by collector.log.collectAsState()

    val SERVICE_ID = "com.bikeride.intercom.spike_a"

    // Permission handling
    val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            collector.addLog("All permissions granted")
        } else {
            collector.recordError("Some permissions denied: ${results.filter { !it.value }.keys}")
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    // Nearby Connections callbacks
    val connectionsClient = remember { Nearby.getConnectionsClient(context) }

    val payloadCallback = remember {
        object : PayloadCallback() {
            override fun onPayloadReceived(endpointId: String, payload: Payload) {
                if (payload.type == Payload.Type.BYTES) {
                    val bytes = payload.asBytes() ?: return
                    packetsReceived++
                    throughputTracker.recordBytes(bytes.size)

                    if (bytes.size >= 9) {
                        val buffer = ByteBuffer.wrap(bytes)
                        val type = buffer.get() // 0 = ping, 1 = pong, 2 = data
                        val id = buffer.getLong()

                        when (type.toInt()) {
                            0 -> {
                                // Received ping — send pong back
                                val pong = ByteBuffer.allocate(9).put(1.toByte()).putLong(id).array()
                                connectionsClient.sendPayload(endpointId, Payload.fromBytes(pong))
                            }
                            1 -> {
                                // Received pong — measure RTT
                                val rtt = latencyTracker.completePong(id)
                                if (rtt != null) {
                                    lastRtt = rtt
                                    collector.recordLatency(rtt, mapOf("ping_id" to id.toString()))
                                    val stats = collector.summary()["rtt"]
                                    avgRtt = stats?.mean
                                }
                            }
                            2 -> {
                                // Data packet — just count for throughput
                                throughputBps = throughputTracker.getBytesPerSecond()
                            }
                        }
                    }
                }
            }

            override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
                if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                    collector.record("payload_transfer_time",
                        update.totalBytes.toDouble(), "bytes",
                        mapOf("endpoint" to endpointId))
                }
            }
        }
    }

    val connectionLifecycleCallback = remember {
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                collector.addLog("Connection initiated with ${info.endpointName}")
                collector.recordEvent("connection_initiated", mapOf("endpoint" to endpointId))
                connectionState = "Authenticating..."
                // Auto-accept for spike testing
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        connectedEndpointId = endpointId
                        connectionState = "Connected ✅"
                        collector.recordEvent("connected", mapOf("endpoint" to endpointId))
                        collector.addLog("Connected! Ready for testing.")
                    }
                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        connectionState = "Rejected"
                        collector.recordError("Connection rejected")
                    }
                    else -> {
                        connectionState = "Failed (${result.status.statusCode})"
                        collector.recordError("Connection failed: ${result.status.statusMessage}")
                    }
                }
            }

            override fun onDisconnected(endpointId: String) {
                connectionState = "Disconnected"
                connectedEndpointId = null
                testRunning = false
                pingJob?.cancel()
                collector.recordEvent("disconnected", mapOf("endpoint" to endpointId))
                collector.addLog("Disconnected from peer")
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            pingJob?.cancel()
            connectionsClient.stopAllEndpoints()
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spike A: Nearby Connections", fontWeight = FontWeight.Bold)
                        Text(connectionState, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        pingJob?.cancel()
                        connectionsClient.stopAllEndpoints()
                        connectionsClient.stopAdvertising()
                        connectionsClient.stopDiscovery()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ═══════════════════════════════════════════════════════
            // Role Selection
            // ═══════════════════════════════════════════════════════
            if (role == null) {
                item {
                    Text("Select Role", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text("Install on BOTH phones. One advertises, one discovers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                role = "advertiser"
                                connectionState = "Advertising..."
                                collector.addLog("Starting as ADVERTISER")
                                val startTime = System.currentTimeMillis()

                                connectionsClient.startAdvertising(
                                    "SpikeA-${Build.MODEL}",
                                    SERVICE_ID,
                                    connectionLifecycleCallback,
                                    AdvertisingOptions.Builder()
                                        .setStrategy(Strategy.P2P_POINT_TO_POINT)
                                        .build()
                                ).addOnSuccessListener {
                                    collector.addLog("Advertising started")
                                    collector.record("advertising_start_time",
                                        (System.currentTimeMillis() - startTime).toDouble(), "ms")
                                }.addOnFailureListener {
                                    connectionState = "Advertising failed"
                                    collector.recordError("Advertising failed: ${it.message}")
                                }
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4FDBC4)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.CellTower, null)
                                Text("Advertise", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                role = "discoverer"
                                connectionState = "Discovering..."
                                val discoveryStart = System.currentTimeMillis()
                                collector.addLog("Starting as DISCOVERER")

                                connectionsClient.startDiscovery(
                                    SERVICE_ID,
                                    object : EndpointDiscoveryCallback() {
                                        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                                            val discoveryTime = System.currentTimeMillis() - discoveryStart
                                            collector.record("discovery_time", discoveryTime.toDouble(), "ms",
                                                mapOf("endpoint" to info.endpointName))
                                            collector.addLog("Found: ${info.endpointName} in ${discoveryTime}ms")
                                            connectionState = "Found peer, connecting..."

                                            connectionsClient.requestConnection(
                                                "SpikeA-${Build.MODEL}",
                                                endpointId,
                                                connectionLifecycleCallback
                                            )
                                        }

                                        override fun onEndpointLost(endpointId: String) {
                                            collector.addLog("Endpoint lost: $endpointId")
                                        }
                                    },
                                    DiscoveryOptions.Builder()
                                        .setStrategy(Strategy.P2P_POINT_TO_POINT)
                                        .build()
                                ).addOnSuccessListener {
                                    collector.addLog("Discovery started")
                                }.addOnFailureListener {
                                    connectionState = "Discovery failed"
                                    collector.recordError("Discovery failed: ${it.message}")
                                }
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5CB8FF)
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Search, null)
                                Text("Discover", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // Test Controls (when connected)
            // ═══════════════════════════════════════════════════════
            if (connectedEndpointId != null) {
                // Live metrics
                item {
                    MetricsCard(
                        lastRtt = lastRtt,
                        avgRtt = avgRtt,
                        pingCount = pingCount,
                        packetsReceived = packetsReceived,
                        throughputBps = throughputBps
                    )
                }

                // Test buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ping test
                        Button(
                            onClick = {
                                if (!testRunning) {
                                    testRunning = true
                                    collector.addLog("Starting RTT ping test (100 pings, 200ms interval)")
                                    pingJob = scope.launch {
                                        repeat(100) {
                                            val id = latencyTracker.startPing()
                                            val ping = ByteBuffer.allocate(9)
                                                .put(0.toByte()).putLong(id).array()
                                            connectedEndpointId?.let { ep ->
                                                connectionsClient.sendPayload(ep, Payload.fromBytes(ping))
                                            }
                                            pingCount = it + 1
                                            delay(200)
                                        }
                                        testRunning = false
                                        collector.addLog("Ping test complete")
                                        val summary = collector.summary()
                                        summary["rtt"]?.let { stats ->
                                            collector.addLog("RTT Summary: ${stats.formatted()}")
                                        }
                                    }
                                }
                            },
                            enabled = !testRunning,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📡 Ping Test")
                        }

                        // Throughput flood test
                        Button(
                            onClick = {
                                if (!testRunning) {
                                    testRunning = true
                                    collector.addLog("Starting throughput flood test (5 seconds)")
                                    pingJob = scope.launch {
                                        val data = ByteBuffer.allocate(100)
                                            .put(2.toByte()) // data type
                                            .putLong(0L) // id
                                            .array()

                                        val startTime = System.currentTimeMillis()
                                        var sent = 0
                                        while (System.currentTimeMillis() - startTime < 5000) {
                                            connectedEndpointId?.let { ep ->
                                                connectionsClient.sendPayload(ep, Payload.fromBytes(data))
                                                sent++
                                            }
                                            delay(20) // 50 pps = simulated audio rate
                                        }
                                        val elapsed = System.currentTimeMillis() - startTime
                                        collector.record("flood_packets_sent", sent.toDouble(), "packets",
                                            mapOf("duration_ms" to elapsed.toString()))
                                        collector.record("flood_pps", sent * 1000.0 / elapsed, "pps")
                                        collector.addLog("Flood test: $sent packets in ${elapsed}ms = ${"%.1f".format(sent * 1000.0 / elapsed)} pps")
                                        testRunning = false
                                    }
                                }
                            },
                            enabled = !testRunning,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🌊 Flood Test")
                        }
                    }
                }

                // Stop test
                if (testRunning) {
                    item {
                        OutlinedButton(
                            onClick = {
                                pingJob?.cancel()
                                testRunning = false
                                collector.addLog("Test stopped")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("⏹ Stop Test")
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // Export results
            // ═══════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val csv = collector.exportCsv()
                            clipboard.setText(AnnotatedString(csv))
                            Toast.makeText(context, "CSV copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📋 Copy CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            val summary = collector.summary()
                            val text = summary.entries.joinToString("\n") { "${it.key}: ${it.value.formatted()}" }
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "Summary copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📊 Copy Summary")
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            // Live Log
            // ═══════════════════════════════════════════════════════
            item {
                Text("Live Log", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            items(logLines.takeLast(50).reversed()) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        "ERROR" in line -> Color(0xFFFF6B6B)
                        "EVENT" in line -> Color(0xFFFFB951)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricsCard(
    lastRtt: Long?,
    avgRtt: Double?,
    pingCount: Int,
    packetsReceived: Int,
    throughputBps: Double
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4FDBC4).copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Live Metrics", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Last RTT", "${lastRtt ?: "—"} ms")
                MetricItem("Avg RTT", avgRtt?.let { "%.1f ms".format(it) } ?: "—")
                MetricItem("Pings", "$pingCount")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Received", "$packetsReceived pkts")
                MetricItem("Throughput", "${"%.0f".format(throughputBps)} B/s")
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = Color(0xFF4FDBC4))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
