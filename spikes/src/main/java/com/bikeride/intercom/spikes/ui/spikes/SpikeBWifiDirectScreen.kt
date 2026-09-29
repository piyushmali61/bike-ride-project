package com.bikeride.intercom.spikes.ui.spikes

import android.Manifest
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import timber.log.Timber

/**
 * Spike B: Wi-Fi Direct Raw UDP
 *
 * Establishes Wi-Fi Direct group and measures raw UDP socket performance.
 * Compare range, latency, and reliability vs Nearby Connections (Spike A).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpikeBWifiDirectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val collector = remember { MeasurementCollector("SpikeB-WifiDirect") }
    val clipboard = LocalClipboardManager.current
    val logLines by collector.log.collectAsState()

    var connectionState by remember { mutableStateOf("Idle") }
    var isGroupOwner by remember { mutableStateOf(false) }
    var groupFormed by remember { mutableStateOf(false) }

    // Wi-Fi Direct manager
    val wifiP2pManager = remember {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    val channel = remember { wifiP2pManager?.initialize(context, context.mainLooper, null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            collector.addLog("Permissions granted")
        } else {
            collector.recordError("Permissions denied: ${results.filter { !it.value }.keys}")
        }
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
        permissionLauncher.launch(perms)
        collector.addLog("Wi-Fi Direct spike initialized")
        collector.addLog("Device: ${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")

        if (wifiP2pManager == null) {
            collector.recordError("Wi-Fi Direct not available on this device")
            connectionState = "Not Available"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spike B: Wi-Fi Direct", fontWeight = FontWeight.Bold)
                        Text(connectionState, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF5CB8FF).copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Test Procedure", fontWeight = FontWeight.SemiBold)
                        Text("1. Create Wi-Fi Direct group on Phone A (Group Owner)\n" +
                             "2. Connect from Phone B\n" +
                             "3. Force 2.4 GHz band for maximum range\n" +
                             "4. Open raw UDP socket and measure RTT/throughput\n" +
                             "5. Walk apart measuring at distance intervals",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp)
                    }
                }
            }

            // Create group
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            connectionState = "Creating group..."
                            collector.addLog("Creating Wi-Fi Direct group (2.4 GHz preferred)")
                            val startTime = System.currentTimeMillis()

                            try {
                                wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        connectionState = "Group Owner ✅"
                                        isGroupOwner = true
                                        groupFormed = true
                                        collector.record("group_create_time", elapsed.toDouble(), "ms")
                                        collector.addLog("Group created in ${elapsed}ms — waiting for peer")
                                    }
                                    override fun onFailure(reason: Int) {
                                        connectionState = "Group creation failed ($reason)"
                                        collector.recordError("createGroup failed: reason=$reason")
                                    }
                                })
                            } catch (e: SecurityException) {
                                collector.recordError("SecurityException: ${e.message}")
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5CB8FF))
                    ) { Text("Create Group\n(Phone A)", fontWeight = FontWeight.Bold) }

                    Button(
                        onClick = {
                            connectionState = "Discovering peers..."
                            collector.addLog("Starting peer discovery")
                            val startTime = System.currentTimeMillis()

                            try {
                                wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        val elapsed = System.currentTimeMillis() - startTime
                                        collector.record("discovery_start_time", elapsed.toDouble(), "ms")
                                        collector.addLog("Discovery started — looking for group owner")
                                        connectionState = "Searching..."
                                    }
                                    override fun onFailure(reason: Int) {
                                        connectionState = "Discovery failed ($reason)"
                                        collector.recordError("discoverPeers failed: reason=$reason")
                                    }
                                })
                            } catch (e: SecurityException) {
                                collector.recordError("SecurityException: ${e.message}")
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FDBC4))
                    ) { Text("Discover\n(Phone B)", fontWeight = FontWeight.Bold) }
                }
            }

            // Info box
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("📝 What to Measure", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall)
                        Text("• Group formation time\n" +
                             "• UDP socket RTT at 10m, 25m, 50m, 100m, 150m, 200m\n" +
                             "• Throughput (flood 100-byte packets at 50 pps)\n" +
                             "• Max range before disconnect\n" +
                             "• Compare 2.4 GHz vs 5 GHz (if configurable)\n" +
                             "• Battery consumption over 15 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Export
            item {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(collector.exportCsv()))
                        Toast.makeText(context, "CSV copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("📋 Copy Results CSV") }
            }

            // Log
            item {
                Text("Live Log", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            items(logLines.takeLast(30).reversed()) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if ("ERROR" in line) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
