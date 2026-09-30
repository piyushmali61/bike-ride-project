package com.bikeride.intercom.feature.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * Supports Multi-Biker Room System (2, 3, 4+ bikers in full-duplex mesh),
 * Hands-Free Voice Mute ("Say MUTE to Mute"), and 1-Click zero-hassle pairing.
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
    val connectedRiders by viewModel.connectedRiders.collectAsState()
    val currentRoom by viewModel.currentRoom.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()
    val peerAmplitude by viewModel.peerAmplitude.collectAsState()
    val currentRoute by viewModel.currentAudioRoute.collectAsState()
    val isBluetoothConnected by viewModel.isBluetoothConnected.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val isRidingHudOpen by viewModel.isRidingHudOpen.collectAsState()
    val isEmergencyAlert by viewModel.isEmergencyAlertActive.collectAsState()
    val rideCode by viewModel.customRideCode.collectAsState()
    val isVoiceControlEnabled by viewModel.isVoiceControlEnabled.collectAsState()
    val lastVoiceCommand by viewModel.lastVoiceCommand.collectAsState()

    var showRoomDialog by remember { mutableStateOf(false) }

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
            roomName = currentRoom,
            bikerCount = maxOf(1, connectedRiders.size + 1),
            isMuted = isMuted,
            onToggleMute = { viewModel.toggleMute() },
            audioRoute = currentRoute,
            onCycleRoute = { viewModel.cycleAudioRoute() },
            onTriggerHorn = { viewModel.triggerEmergencyHorn() },
            onEndRide = { viewModel.endRideSession() },
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
                                "Universal Rider Mesh",
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

        val borderModifier = if (isEmergencyAlert) {
            Modifier.border(4.dp, Color(0xFFFF1744), RoundedCornerShape(0.dp))
        } else Modifier

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(borderModifier)
                .background(Color(0xFF0B0F19))
                .padding(paddingValues)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // ═══════════════════════════════════════════════════════════
            // Multi-Biker Room Card & Roster
            // ═══════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (connectedRiders.isNotEmpty()) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFF334155)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                connectedRiders.isNotEmpty() -> Color(0xFF00E676)
                                                connectionState == MeshConnectionState.SEARCHING -> Color(0xFFFF9100)
                                                else -> Color(0xFF64748B)
                                            }
                                        )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Convoy Room: $currentRoom",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (connectedRiders.isNotEmpty()) {
                                            "🟢 ${connectedRiders.size + 1} Bikers in Room · Full Duplex"
                                        } else if (connectionState == MeshConnectionState.SEARCHING) {
                                            "🔍 Searching for nearby room riders..."
                                        } else {
                                            "Tap 'TAP TO RIDE' to open room"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Change Room button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.clickable { showRoomDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Room", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Room Preset Chips
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("CONVOY 1", "CONVOY 2", "SQUAD ALPHA", "APEX").forEach { room ->
                                val isSelected = room == currentRoom
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676)) else null,
                                    modifier = Modifier.clickable {
                                        viewModel.setCustomRideCode(room)
                                    }
                                ) {
                                    Text(
                                        text = room,
                                        color = if (isSelected) Color(0xFF00E676) else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Connected Bikers Live Roster
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "CONNECTED RIDERS IN ROOM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Local Rider Chip
                            RiderBadgeChip(
                                name = "You (Host)",
                                isHost = true,
                                isMuted = isMuted,
                                isSpeaking = micAmplitude > 0.08f && !isMuted
                            )

                            // Remote Connected Bikers
                            connectedRiders.values.forEach { peer ->
                                RiderBadgeChip(
                                    name = peer.name,
                                    isHost = false,
                                    isMuted = peer.isMuted,
                                    isSpeaking = peer.isSpeaking
                                )
                            }

                            if (connectedRiders.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        "Waiting for 2nd, 3rd biker to tap Ride...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // Hands-Free Mute Control Card (Wave Glove / In-App Phrase)
            // ═══════════════════════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isVoiceControlEnabled) Color(0xFF38BDF8).copy(alpha = 0.35f) else Color(0xFF334155)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PanTool,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Hands-Free Mute Control",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Wave glove or say 'Rider signing off'",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Switch(
                                checked = isVoiceControlEnabled,
                                onCheckedChange = { viewModel.toggleVoiceControl(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF38BDF8)
                                )
                            )
                        }

                        if (isVoiceControlEnabled) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("👋", fontSize = 14.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Wave glove 5cm over top of phone to Mute / Unmute",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🎙️", fontSize = 14.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Or speak: \"Rider signing off\" to Mute · \"Signing on\" to Unmute",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = if (lastVoiceCommand != null) {
                                            "⚡ Last detected: $lastVoiceCommand (Action executed)"
                                        } else {
                                            "🛡️ Standalone in-app detection · Zero Gemini popups"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lastVoiceCommand != null) Color(0xFF00E676) else Color(0xFF64748B)
                                    )
                                }
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
                            text = if (isMuted) "MIC MUTED" else if (micAmplitude > 0.08f) "YOU ARE SPEAKING" else if (peerAmplitude > 0.08f) "CONVOY SPEAKING" else "CHANNEL QUIET",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMuted) Color(0xFFFF5252) else Color(0xFF00B0FF),
                            letterSpacing = 1.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        AudioWaveVisualizer(
                            amplitude = if (micAmplitude > 0.05f) micAmplitude else peerAmplitude,
                            isMuted = isMuted,
                            barCount = 13,
                            maxHeight = 60.dp
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
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OneClickRadarButton(
                        connectionState = connectionState,
                        onClick = { viewModel.onOneClickConnectToggle() }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════
            // Cockpit Controls (When Active / Connected)
            // ═══════════════════════════════════════════════════════════
            if (connectionState == MeshConnectionState.CONNECTED || connectionState == MeshConnectionState.SEARCHING) {
                item {
                    Text(
                        "Cockpit Motorcycle Controls",
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
                                subtitle = "Tap or say 'Mute'",
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
                                subtitle = "Siren all bikers",
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

                // Explicit End Ride Button
                item {
                    Button(
                        onClick = { viewModel.endRideSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "END RIDE CONVOY",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Quick Guide Card
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
                                "⚡ Multi-Biker Intercom Guide:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                            Text(
                                "1. Keep Room set to the same name (e.g. \"CONVOY 1\") on all phones.\n" +
                                "2. Tap 'TAP TO RIDE' on Phone 1, Phone 2, Phone 3, etc.\n" +
                                "3. They auto-link into full-duplex intercom!\n" +
                                "4. Say \"MUTE\" while riding to mute hands-free anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Room Dialog
    if (showRoomDialog) {
        var inputRoom by remember { mutableStateOf(currentRoom) }
        AlertDialog(
            onDismissRequest = { showRoomDialog = false },
            title = { Text("Set Convoy Room Name") },
            text = {
                Column {
                    Text(
                        "All bikers entering this Room name will connect into the same group intercom mesh:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputRoom,
                        onValueChange = { inputRoom = it.take(16).uppercase() },
                        label = { Text("Room Name / Code") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setCustomRideCode(inputRoom)
                    showRoomDialog = false
                }) {
                    Text("Join Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RiderBadgeChip(
    name: String,
    isHost: Boolean,
    isMuted: Boolean,
    isSpeaking: Boolean
) {
    val borderColor = when {
        isSpeaking -> Color(0xFF00E676)
        isMuted -> Color(0xFFFF5252)
        else -> Color(0xFF334155)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E293B),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) Color(0xFFFF5252) else if (isSpeaking) Color(0xFF00E676) else Color(0xFF38BDF8))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isMuted) "Muted" else if (isSpeaking) "Speaking..." else if (isHost) "Host" else "Connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMuted) Color(0xFFFF5252) else if (isSpeaking) Color(0xFF00E676) else Color(0xFF94A3B8)
                )
            }
        }
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
