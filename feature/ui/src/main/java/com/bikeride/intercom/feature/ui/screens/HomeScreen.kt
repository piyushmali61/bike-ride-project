package com.bikeride.intercom.feature.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bikeride.intercom.bluetooth.AudioRouteType
import com.bikeride.intercom.feature.ui.IntercomViewModel
import com.bikeride.intercom.feature.ui.components.AudioWaveVisualizer
import com.bikeride.intercom.feature.ui.components.OneClickRadarButton
import com.bikeride.intercom.feature.ui.components.RidingHudOverlay
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState

/**
 * AstraRide Unified Cockpit
 *
 * An intuitive, glove-friendly, zero-hassle 1-Click motorcycle intercom interface.
 * Built for seamless pairing between any Android devices (including Samsung M35 & S25 FE).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: IntercomViewModel = hiltViewModel(),
    onCreateConnection: () -> Unit = {},
    onJoinConnection: () -> Unit = {},
    onStartSession: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRidingMode: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedPeerName by viewModel.connectedPeerName.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val peerIsMuted by viewModel.peerIsMuted.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()
    val peerAmplitude by viewModel.peerAmplitude.collectAsState()
    val currentRoute by viewModel.currentAudioRoute.collectAsState()
    val isBluetoothConnected by viewModel.isBluetoothConnected.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val isRidingHudOpen by viewModel.isRidingHudOpen.collectAsState()
    val isEmergencyAlert by viewModel.isEmergencyAlertActive.collectAsState()
    val rideCode by viewModel.customRideCode.collectAsState()

    var showRideCodeDialog by remember { mutableStateOf(false) }

    // Required Android Permissions across API 29-35
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModel.onPermissionsResult(allGranted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    // Full-screen Riding HUD Mode
    if (isRidingHudOpen) {
        RidingHudOverlay(
            isMuted = isMuted,
            onToggleMute = { viewModel.toggleMute() },
            audioRoute = currentRoute,
            onCycleRoute = { viewModel.cycleAudioRoute() },
            onTriggerHorn = { viewModel.triggerEmergencyHorn() },
            peerName = connectedPeerName,
            latencyMs = latencyMs,
            amplitude = if (micAmplitude > 0.05f) micAmplitude else peerAmplitude,
            onExitHud = { viewModel.toggleRidingHud(false) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏍️", fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "AstraRide",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                },
                actions = {
                    // Audio route quick badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { viewModel.cycleAudioRoute() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = when (currentRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                                    AudioRouteType.LOUDSPEAKER -> Icons.Filled.VolumeUp
                                    AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                                },
                                contentDescription = null,
                                tint = if (isBluetoothConnected) Color(0xFF00E676) else Color(0xFF00B0FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (currentRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> "Helmet"
                                    AudioRouteType.LOUDSPEAKER -> "Speaker"
                                    AudioRouteType.EARPIECE -> "Earpiece"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        }
    ) { paddingValues ->

        // Screen border flash effect on emergency horn
        val borderModifier = if (isEmergencyAlert) {
            Modifier.border(4.dp, Color(0xFFFF1744), RoundedCornerShape(0.dp))
        } else Modifier

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(borderModifier)
                .background(Color(0xFF0B0F19))
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            // ═══════════════════════════════════════════════════════════
            // Status & Link Card
            // ═══════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (connectionState) {
                            MeshConnectionState.CONNECTED -> Color(0xFF00E676).copy(alpha = 0.4f)
                            MeshConnectionState.SEARCHING -> Color(0xFFFF9100).copy(alpha = 0.4f)
                            else -> Color(0xFF334155)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (connectionState) {
                                            MeshConnectionState.CONNECTED -> Color(0xFF00E676)
                                            MeshConnectionState.SEARCHING -> Color(0xFFFF9100)
                                            MeshConnectionState.CONNECTING -> Color(0xFF00B0FF)
                                            else -> Color(0xFF64748B)
                                        }
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = when (connectionState) {
                                        MeshConnectionState.CONNECTED -> "P2P Mesh Link Active"
                                        MeshConnectionState.SEARCHING -> "Seeking Nearby Rider..."
                                        MeshConnectionState.CONNECTING -> "Establishing Secure Link..."
                                        else -> "Mesh Ready · Offline Mode"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when (connectionState) {
                                        MeshConnectionState.CONNECTED -> "Peer: ${connectedPeerName ?: "Rider"} · 100% Quality"
                                        MeshConnectionState.SEARCHING -> "Bring devices close & tap Ride on both"
                                        else -> "Zero internet · Wi-Fi Direct + Bluetooth"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (connectionState == MeshConnectionState.CONNECTED) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "${latencyMs}ms",
                                    color = Color(0xFF00E676),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // Dynamic Audio Waveform Visualizer
            // ═══════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (connectionState == MeshConnectionState.CONNECTED) {
                                if (isMuted) "MIC MUTED" else if (micAmplitude > 0.08f) "YOU ARE SPEAKING" else if (peerAmplitude > 0.08f) "RIDER SPEAKING" else "CHANNEL QUIET"
                            } else "VOICE RADAR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMuted) Color(0xFFFF5252) else Color(0xFF00B0FF),
                            letterSpacing = 1.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        AudioWaveVisualizer(
                            amplitude = if (micAmplitude > 0.05f) micAmplitude else peerAmplitude,
                            isMuted = isMuted && connectionState == MeshConnectionState.CONNECTED,
                            barCount = 11,
                            maxHeight = 64.dp
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // Primary 1-Click Radar Button
            // ═══════════════════════════════════════════════════════════
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OneClickRadarButton(
                        connectionState = connectionState,
                        onClick = { viewModel.onOneClickConnectToggle() }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════
            // Active Cockpit Controls (When Connected)
            // ═══════════════════════════════════════════════════════════
            if (connectionState == MeshConnectionState.CONNECTED) {
                item {
                    Text(
                        "Rider Cockpit Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // 2x2 Glove-Friendly Tactical Button Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Button 1: MIC MUTE TOGGLE
                            CockpitButton(
                                modifier = Modifier.weight(1f),
                                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                title = if (isMuted) "MUTED" else "MIC LIVE",
                                subtitle = "Tap to toggle",
                                color = if (isMuted) Color(0xFFFF1744) else Color(0xFF00E676),
                                onClick = { viewModel.toggleMute() }
                            )

                            // Button 2: AUDIO ROUTE SWITCH
                            CockpitButton(
                                modifier = Modifier.weight(1f),
                                icon = when (currentRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                                    AudioRouteType.LOUDSPEAKER -> Icons.Filled.VolumeUp
                                    AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                                },
                                title = when (currentRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> "HELMET"
                                    AudioRouteType.LOUDSPEAKER -> "SPEAKER"
                                    AudioRouteType.EARPIECE -> "EARPIECE"
                                },
                                subtitle = "Audio Route",
                                color = Color(0xFF00B0FF),
                                onClick = { viewModel.cycleAudioRoute() }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Button 3: CONVOY HORN / SOS ALERT
                            CockpitButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Campaign,
                                title = "ALERT HORN",
                                subtitle = "Sound convoy siren",
                                color = Color(0xFFFF9100),
                                onClick = { viewModel.triggerEmergencyHorn() }
                            )

                            // Button 4: FULLSCREEN RIDING HUD
                            CockpitButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.DashboardCustomize,
                                title = "RIDING HUD",
                                subtitle = "OLED Handlebar view",
                                color = Color(0xFFA855F7),
                                onClick = { viewModel.toggleRidingHud(true) }
                            )
                        }
                    }
                }

                // Volume Wind Booster Slider
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Wind Noise Volume Boost",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "+${((volumeBoost - 1f) * 4f).toInt() * 3} dB",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = volumeBoost,
                                onValueChange = { viewModel.setVolumeBoost(it) },
                                valueRange = 1.0f..4.0f,
                                steps = 3,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E676),
                                    activeTrackColor = Color(0xFF00E676),
                                    inactiveTrackColor = Color(0xFF334155)
                                )
                            )
                        }
                    }
                }

                // Disconnect End Ride Button
                item {
                    Button(
                        onClick = { viewModel.onOneClickConnectToggle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "END RIDE SESSION",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // ═══════════════════════════════════════════════════════════
                // Idle Settings & Ride Code
                // ═══════════════════════════════════════════════════════════
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRideCodeDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF00B0FF).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Group, contentDescription = null, tint = Color(0xFF00B0FF))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Ride Group Mesh",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Code: $rideCode (Tap to change)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF64748B))
                        }
                    }
                }

                // Quick explanation tip
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "⚡ How to connect in 1-Click:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                            Text(
                                "1. Install this AstraRide app on Phone 1 (e.g. Samsung M35) and Phone 2 (e.g. Samsung S25 FE).\n" +
                                "2. Tap the big 'TAP TO RIDE' button on both phones.\n" +
                                "3. They automatically find each other, pair instantly, and connect crystal-clear voice intercom!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Ride Code Dialog
    if (showRideCodeDialog) {
        var inputCode by remember { mutableStateOf(rideCode) }
        AlertDialog(
            onDismissRequest = { showRideCodeDialog = false },
            title = { Text("Set Private Ride Group Code") },
            text = {
                Column {
                    Text(
                        "Set matching codes on both phones to ensure you pair exclusively with your riding partner:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.take(6).uppercase() },
                        label = { Text("Ride Code") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setCustomRideCode(inputCode)
                    showRideCodeDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRideCodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CockpitButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(105.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
