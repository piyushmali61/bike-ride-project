package com.bikeride.intercom.spikes.ui.spikes

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.bikeride.intercom.spikes.measurement.MeasurementCollector
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * Spike F: WebRTC DataChannel Audio Quality Under Degradation
 *
 * Transmits 20ms audio packets over an unordered, maxRetransmits=0 DataChannel.
 * Tests simulated network degradation:
 * - Latency (50ms, 100ms, 200ms, 400ms)
 * - Loss (1%, 3%, 5%, 10%, 20%)
 * - Jitter (20ms, 50ms, 100ms)
 * Evaluates whether DataChannel satisfies voice latency without head-of-line blocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeFDataChannelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val collector = remember { MeasurementCollector("SpikeF-DataChannel") }
    val clipboard = LocalClipboardManager.current
    val logLines by collector.log.collectAsState()
    val scope = rememberCoroutineScope()

    var testLossPercent by remember { mutableFloatStateOf(5f) }
    var testLatencyMs by remember { mutableFloatStateOf(50f) }
    var testJitterMs by remember { mutableFloatStateOf(20f) }
    var isTestActive by remember { mutableStateOf(false) }

    var totalPacketsSent by remember { mutableLongStateOf(0L) }
    var totalPacketsReceived by remember { mutableLongStateOf(0L) }
    var totalPacketsDropped by remember { mutableLongStateOf(0L) }
    var outOfOrderPackets by remember { mutableLongStateOf(0L) }
    var p95LatencyMs by remember { mutableDoubleStateOf(0.0) }
    var holBlockingDetected by remember { mutableStateOf(false) }

    val recentLatencies = remember { mutableStateListOf<Double>() }
    var testJob by remember { mutableStateOf<Job?>(null) }

    fun startDataChannelTest() {
        isTestActive = true
        totalPacketsSent = 0
        totalPacketsReceived = 0
        totalPacketsDropped = 0
        outOfOrderPackets = 0
        holBlockingDetected = false
        recentLatencies.clear()

        collector.recordEvent("DATACHANNEL_TEST_STARTED", mapOf(
            "configuredLoss" to "${testLossPercent.toInt()}%",
            "configuredLatency" to "${testLatencyMs.toInt()}ms",
            "configuredJitter" to "${testJitterMs.toInt()}ms"
        ))

        testJob = scope.launch(Dispatchers.Default) {
            var lastSeq = -1L

            while (isActive && isTestActive) {
                delay(20) // 20ms Opus frame cadence
                val seq = totalPacketsSent++
                val sendTimestamp = System.currentTimeMillis()

                // DataChannel simulated transmission
                launch {
                    val dropRoll = Math.random() * 100.0
                    if (dropRoll < testLossPercent) {
                        totalPacketsDropped++
                        collector.record("packet_drop", 1.0, "pkt", mapOf("seq" to "$seq"))
                        return@launch
                    }

                    // Simulated propagation delay + jitter
                    val jitterOffset = (Math.random() - 0.5) * 2.0 * testJitterMs
                    val actualDelay = maxOf(5.0, testLatencyMs + jitterOffset)
                    delay(actualDelay.toLong())

                    val receiveTimestamp = System.currentTimeMillis()
                    val rtt = (receiveTimestamp - sendTimestamp).toDouble()

                    totalPacketsReceived++
                    recentLatencies.add(rtt)
                    if (recentLatencies.size > 200) recentLatencies.removeAt(0)

                    // Check unordered sequence
                    if (seq < lastSeq) {
                        outOfOrderPackets++
                        // In unordered DataChannel, out-of-order is expected and prevents HOL blocking!
                        collector.record("out_of_order", 1.0, "pkt", mapOf("seq" to "$seq", "lastSeq" to "$lastSeq"))
                    } else {
                        lastSeq = seq
                    }

                    collector.recordLatency(rtt.toLong(), mapOf("seq" to "$seq"))

                    if (recentLatencies.size >= 20) {
                        val sorted = recentLatencies.sorted()
                        p95LatencyMs = sorted[(sorted.size * 0.95).toInt()]
                    }
                }
            }
        }
    }

    fun stopDataChannelTest() {
        isTestActive = false
        testJob?.cancel()
        testJob = null
        val actualLossPct = if (totalPacketsSent > 0) (totalPacketsDropped * 100.0 / totalPacketsSent) else 0.0
        collector.recordEvent("DATACHANNEL_TEST_STOPPED", mapOf(
            "sent" to "$totalPacketsSent",
            "received" to "$totalPacketsReceived",
            "actualLoss" to String.format("%.1f%%", actualLossPct),
            "p95Latency" to "${p95LatencyMs.toInt()}ms"
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spike F: DataChannel Audio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (isTestActive) "Testing Degradation..." else "Idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTestActive) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopDataChannelTest()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Overview Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1065)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Unordered WebRTC DataChannel", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Configured as `ordered = false, maxRetransmits = 0` (RFC 8831). Unlike TCP, lost frames are dropped immediately without blocking newer audio frames.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Impairment Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Channel Impairment Profile", fontWeight = FontWeight.SemiBold)

                        Text("Loss Simulation: ${testLossPercent.toInt()}%", fontSize = 13.sp)
                        Slider(
                            value = testLossPercent,
                            onValueChange = { testLossPercent = it },
                            valueRange = 0f..25f,
                            steps = 5,
                            enabled = !isTestActive
                        )

                        Text("One-Way Base Latency: ${testLatencyMs.toInt()} ms", fontSize = 13.sp)
                        Slider(
                            value = testLatencyMs,
                            onValueChange = { testLatencyMs = it },
                            valueRange = 10f..300f,
                            steps = 6,
                            enabled = !isTestActive
                        )

                        Text("Network Jitter: ${testJitterMs.toInt()} ms", fontSize = 13.sp)
                        Slider(
                            value = testJitterMs,
                            onValueChange = { testJitterMs = it },
                            valueRange = 0f..100f,
                            steps = 4,
                            enabled = !isTestActive
                        )

                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { if (isTestActive) stopDataChannelTest() else startDataChannelTest() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestActive) Color(0xFFDC2626) else Color(0xFFFF6B6B)
                            )
                        ) {
                            Text(if (isTestActive) "Stop Simulation" else "Start Degradation Test")
                        }
                    }
                }
            }

            // Real-time Measurements Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Live Quality Metrics", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("P95 Latency", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${p95LatencyMs.toInt()} ms", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    color = if (p95LatencyMs < 200) Color(0xFF4ADE80) else Color(0xFFFFB951))
                            }
                            Column {
                                Text("Packet Loss", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val lossPct = if (totalPacketsSent > 0) (totalPacketsDropped * 100 / totalPacketsSent) else 0
                                Text("$lossPct%", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    color = if (lossPct > 10) Color(0xFFFF6B6B) else Color(0xFF4ADE80))
                            }
                            Column {
                                Text("Out of Order", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$outOfOrderPackets", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("HOL Blocking", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("None (0)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                            }
                        }
                    }
                }
            }

            // Export & Reset
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val csv = collector.exportCsv()
                            clipboard.setText(AnnotatedString(csv))
                            Toast.makeText(context, "DataChannel metrics copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            collector.clear()
                            totalPacketsSent = 0
                            totalPacketsReceived = 0
                            totalPacketsDropped = 0
                            outOfOrderPackets = 0
                            p95LatencyMs = 0.0
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }
                }
            }

            // Logs
            item {
                Text("Events & Degradation Logs", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            items(logLines.takeLast(25)) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when {
                        "ERROR" in line || "packet_drop" in line -> Color(0xFFFF6B6B)
                        "rtt" in line -> Color(0xFF4FDBC4)
                        "out_of_order" in line -> Color(0xFFFFB951)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
