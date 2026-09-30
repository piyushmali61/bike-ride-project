package com.bikeride.intercom.feature.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.bluetooth.AudioRouteType
import com.bikeride.intercom.feature.ui.IntercomViewModel
import com.bikeride.intercom.feature.ui.components.AudioWaveVisualizer
import com.bikeride.intercom.feature.ui.components.RidingHudOverlay
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState
import java.util.Calendar

/**
 * High-End Sports Bike Glassmorphism Cockpit UI.
 *
 * Designed to strictly reflect the dark sports bike aesthetics:
 * - Pitch-black midnight canvas (#090D16) with frosted glass cards (rgba(19, 29, 45, 0.85))
 * - Hero Sports Bike showcase with real-time Convoy metrics
 * - Central Giant Glowing Red Circular Ride Action button (Start/End Ride)
 * - Zero-Touch Hands-Free Mute (Glove Wave / Double-Tap phone mount)
 * - Custom Rider Name customization with persistent Convoy broadcasting
 * - Floating glass bottom navigation bar (Home, Rides, Garage, Security, Profile)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: IntercomViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onSettings: () -> Unit = {},
    onRidingMode: () -> Unit = {},
    onOpenChat: () -> Unit = {}
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
    val lastVoiceCommand by viewModel.lastVoiceCommand.collectAsState()
    val riderName by viewModel.riderName.collectAsState()
    val bikeModel by viewModel.bikeModel.collectAsState()
    val selectedRoom by viewModel.customRideCode.collectAsState()
    val riderProfile by viewModel.riderProfile.collectAsState()
    val meshUnread by viewModel.meshUnread.collectAsState()
    val meshBtLinks by viewModel.meshBluetoothLinks.collectAsState()
    val meshRelays by viewModel.meshInternetRelays.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()

    var showNameDialog by remember { mutableStateOf(false) }
    var showBikeDialog by remember { mutableStateOf(false) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableStateOf(0) }

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

    // Dynamic greeting based on time of day
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // Full-Screen OLED Handlebar HUD
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
            onExitHud = { viewModel.toggleRidingHud(false) },
            isEmergencyAlert = isEmergencyAlert
        )
        return
    }

    val isRideActive = connectionState == MeshConnectionState.CONNECTED ||
        connectionState == MeshConnectionState.SEARCHING ||
        connectionState == MeshConnectionState.CONNECTING

    Scaffold(
        modifier = modifier,
        containerColor = AstraBg,
        topBar = {
            AstraTopBar(
                currentRoute = currentRoute,
                isBluetoothConnected = isBluetoothConnected,
                onCycleRoute = { viewModel.cycleAudioRoute() },
                unread = meshUnread,
                onOpenChat = onOpenChat
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (isEmergencyAlert) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("ALERT HORN SOUNDING! CONVOY SOS ACTIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                ConvoyRoomPanel(
                    roomName = selectedRoom,
                    connectionState = connectionState,
                    connectedRiders = connectedRiders.values.toList(),
                    myRiderName = riderName,
                    isMuted = isMuted,
                    onSelectPreset = { viewModel.setCustomRideCode(it) },
                    onEditRoom = { showRoomDialog = true },
                    myProfile = riderProfile,
                    onEditProfile = { showNameDialog = true }
                )
            }

            item {
                MeshChatCard(
                    unread = meshUnread,
                    bluetoothLinks = meshBtLinks,
                    internetRelays = meshRelays,
                    reachableRiders = meshPeers.values.count {
                        System.currentTimeMillis() - it.lastSeen < com.bikeride.intercom.mesh.ConvoyMesh.PEER_ACTIVE_MS
                    },
                    onOpen = onOpenChat
                )
            }

            item {
                HandsFreeMutePanel(
                    isMuted = isMuted,
                    lastVoiceCommand = lastVoiceCommand,
                    onToggleMute = { viewModel.toggleMute() }
                )
            }

            item {
                ChannelActivityPanel(
                    isActive = isRideActive,
                    isMuted = isMuted,
                    micAmplitude = micAmplitude,
                    peerAmplitude = peerAmplitude
                )
            }

            item {
                TapToRideButton(
                    isActive = isRideActive,
                    connectionState = connectionState,
                    onClick = { viewModel.onOneClickConnectToggle() }
                )
            }

            // Secondary actions kept from the previous design: HUD, horn, rider name, bike
            item {
                QuickActionRow(
                    riderName = riderName,
                    bikeModel = bikeModel,
                    onOpenHud = { viewModel.toggleRidingHud(true) },
                    onAlertHorn = { viewModel.triggerEmergencyHorn() },
                    onEditName = { showNameDialog = true },
                    onEditBike = { showBikeDialog = true }
                )
            }

            item {
                AudioControlCard(
                    currentRoute = currentRoute,
                    isBluetoothConnected = isBluetoothConnected,
                    volumeBoost = volumeBoost,
                    onCycleRoute = { viewModel.cycleAudioRoute() },
                    onVolumeBoostChange = { viewModel.setVolumeBoost(it) }
                )
            }

            item { IntercomGuideCard() }

            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }


    // ═══════════════════════════════════════════════════════════════════
    // DIALOGS: RIDER NAME, BIKE MODEL, ROOM SWITCH
    // ═══════════════════════════════════════════════════════════════════
    if (showNameDialog) {
        ProfileDialog(
            current = riderProfile,
            onDismiss = { showNameDialog = false },
            onSave = { profile ->
                viewModel.updateProfile(profile)
                showNameDialog = false
            }
        )
    }

    if (showBikeDialog) {
        BikeModelDialog(
            currentModel = bikeModel,
            onDismiss = { showBikeDialog = false },
            onSave = { newModel ->
                viewModel.setBikeModel(newModel)
                showBikeDialog = false
            }
        )
    }

    if (showRoomDialog) {
        RoomSwitchDialog(
            currentRoom = currentRoom,
            onDismiss = { showRoomDialog = false },
            onSelectRoom = { newRoom ->
                viewModel.setCustomRideCode(newRoom)
                showRoomDialog = false
            }
        )
    }
}

/**
 * Hero Sports Bike Glassmorphism Card with 3 Metrics.
 */
@Composable
fun SportsBikeHeroCard(
    bikeModel: String,
    connectionState: MeshConnectionState,
    latencyMs: Long,
    connectedCount: Int,
    onEditBike: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF2A3B53))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Model & Connection Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onEditBike)
                ) {
                    Column {
                        Text(
                            text = "ASTRARIDE",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bikeModel,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = Color(0xFFFF2A42), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Status Pill
                val isOnline = connectionState == MeshConnectionState.CONNECTED || connectionState == MeshConnectionState.SEARCHING
                val statusColor = if (isOnline) Color(0xFF00E676) else Color(0xFF94A3B8)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "Connected" else "Idle",
                            color = statusColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Sports Bike Neon Silhouette Canvas Art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0A0F1A)),
                contentAlignment = Alignment.Center
            ) {
                SportsBikeNeonCanvas()
            }

            Spacer(Modifier.height(18.dp))

            // 3 Glass Metrics in a Horizontal Row (Top Speed, Range, Mileage/Signal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassMetricBadge(
                    modifier = Modifier.weight(1f),
                    value = "120",
                    unit = "KM/H",
                    label = "Top Speed"
                )
                GlassMetricBadge(
                    modifier = Modifier.weight(1f),
                    value = "${connectedCount}x",
                    unit = "CONVOY",
                    label = "Bikers"
                )
                GlassMetricBadge(
                    modifier = Modifier.weight(1f),
                    value = "${latencyMs}ms",
                    unit = "99%",
                    label = "Hotspot Link"
                )
            }
        }
    }
}

/**
 * Metric Badge Tile inside Hero Card.
 */
@Composable
fun GlassMetricBadge(
    modifier: Modifier = Modifier,
    value: String,
    unit: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF172336),
        border = BorderStroke(1.dp, Color(0xFF2A3D58))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = unit,
                color = Color(0xFFFF2A42),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Stylized Canvas drawing of a sports superbike neon profile with glowing speedlines.
 */
@Composable
fun SportsBikeNeonCanvas() {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        // Background subtle grid/speed lines
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(0f, h * 0.85f),
            end = Offset(w, h * 0.85f),
            strokeWidth = 2f
        )

        // Superbike Frame & Fairing Path
        val bikePath = Path().apply {
            // Front Wheel Center
            moveTo(w * 0.25f, h * 0.75f)
            // Fairing up to handlebar
            lineTo(w * 0.38f, h * 0.38f)
            // Windshield & aerodynamic cowl
            lineTo(w * 0.44f, h * 0.28f)
            // Fuel tank curve
            cubicTo(w * 0.48f, h * 0.22f, w * 0.54f, h * 0.25f, w * 0.58f, h * 0.40f)
            // Rider seat
            lineTo(w * 0.68f, h * 0.44f)
            // Pillion tail cowl
            lineTo(w * 0.80f, h * 0.32f)
            // Rear subframe
            lineTo(w * 0.74f, h * 0.75f)
            // Swingarm to engine
            lineTo(w * 0.50f, h * 0.68f)
            close()
        }

        // Glow pass
        drawPath(
            path = bikePath,
            brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2A42))),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // Front Wheel Rim
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = h * 0.22f,
            center = Offset(w * 0.25f, h * 0.75f),
            style = Stroke(width = 3.dp.toPx())
        )
        // Rear Wheel Rim
        drawCircle(
            color = Color(0xFFFF2A42),
            radius = h * 0.22f,
            center = Offset(w * 0.75f, h * 0.75f),
            style = Stroke(width = 3.dp.toPx())
        )

        // Twin LED Headlight Glow
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = 5.dp.toPx(),
            center = Offset(w * 0.43f, h * 0.33f)
        )

        // LED Taillight Glow
        drawCircle(
            color = Color(0xFFFF1744),
            radius = 6.dp.toPx(),
            center = Offset(w * 0.81f, h * 0.33f)
        )
    }
}

/**
 * The 3 Circular Action Controls (Matching screenshot):
 * Left: Live HUD / Tracking
 * Center: GIANT GLOWING RED CIRCULAR RIDE BUTTON (Start/End Ride)
 * Right: Alert Horn
 */
@Composable
fun CentralCockpitActionCluster(
    isActive: Boolean,
    onToggleRide: () -> Unit,
    onOpenHud: () -> Unit,
    onAlertHorn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Button 1 (Left): Live HUD / Tracking
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF131D2D))
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onOpenHud),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Timeline,
                    contentDescription = "Live Tracking",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Live HUD", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }

        // Button 2 (Center): GIANT GLOWING RED CIRCULAR BUTTON (Start / End Ride)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .shadow(16.dp, CircleShape, spotColor = Color(0xFFFF1744), ambientColor = Color(0xFFFF2A42))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isActive) Color(0xFFFF1744) else Color(0xFFFF334B),
                                Color(0xFFB71C1C)
                            )
                        )
                    )
                    .border(3.dp, Color(0xFFFF8A80), CircleShape)
                    .clickable(onClick = onToggleRide),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isActive) "End Ride" else "Start Ride",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isActive) "End Ride" else "Start Ride",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Button 3 (Right): Alert Horn
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF131D2D))
                    .border(1.5.dp, Color(0xFFFF9100).copy(alpha = 0.6f), CircleShape)
                    .clickable(onClick = onAlertHorn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = "Alert Horn",
                    tint = Color(0xFFFF9100),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Alert Horn", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Hands-Free Zero-Touch Mute Card.
 * Gives riders instant tactile & visual feedback that waving or double-tapping mount toggles mute without touching screen!
 */
@Composable
fun HandsFreeZeroTouchMuteCard(
    isMuted: Boolean,
    sensorStatus: String,
    onToggleMute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, if (isMuted) Color(0xFFFF1744).copy(alpha = 0.6f) else Color(0xFF00E676).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PanTool,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hands-Free Zero-Touch Mute",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wave glove over phone or double-tap mount",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "SENSOR: $sensorStatus",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Interactive Mute Pill
            val pillColor = if (isMuted) Color(0xFFFF1744) else Color(0xFF00E676)
            Surface(
                modifier = Modifier.clickable(onClick = onToggleMute),
                shape = RoundedCornerShape(50),
                color = pillColor.copy(alpha = 0.2f),
                border = BorderStroke(1.5.dp, pillColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = pillColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isMuted) "MUTED" else "MIC LIVE",
                        color = pillColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/**
 * Convoy Room Card (Matches "Last Ride" card in the screenshot!)
 */
@Composable
fun ConvoyRoomCard(
    roomName: String,
    connectedRiders: List<com.bikeride.intercom.transport.local.nearby.ConnectedRider>,
    myRiderName: String,
    isMuted: Boolean,
    micAmplitude: Float,
    peerAmplitude: Float,
    onSwitchRoom: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF2A3B53))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Convoy Room & Switch Room button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Convoy Room",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "ROOM: $roomName",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                TextButton(
                    onClick = onSwitchRoom,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF2A42))
                ) {
                    Text("SWITCH ROOM", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Neon Highway Map Track Preview (Matching screenshot mini map curve)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C121E)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val w = size.width
                    val h = size.height

                    val path = Path().apply {
                        moveTo(w * 0.05f, h * 0.7f)
                        cubicTo(w * 0.3f, h * 0.2f, w * 0.6f, h * 0.9f, w * 0.95f, h * 0.3f)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2A42))),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Start dot (Green)
                    drawCircle(Color(0xFF00E676), radius = 4.dp.toPx(), center = Offset(w * 0.05f, h * 0.7f))
                    // End dot (Red)
                    drawCircle(Color(0xFFFF1744), radius = 4.dp.toPx(), center = Offset(w * 0.95f, h * 0.3f))
                }
                Text(
                    text = "Convoy Intercom Active · ${connectedRiders.size + 1} Connected Bikers",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))

            // Connected Bikers Roster with User Names
            Text("Convoy Members", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Self Chip
            RiderRosterChip(
                name = "$myRiderName (You)",
                isSelf = true,
                isMuted = isMuted,
                amplitude = micAmplitude
            )

            // Peer Chips
            connectedRiders.forEach { peer ->
                Spacer(Modifier.height(6.dp))
                RiderRosterChip(
                    name = peer.name,
                    isSelf = false,
                    isMuted = peer.isMuted,
                    amplitude = peerAmplitude
                )
            }
        }
    }
}

/**
 * Chip representing a connected biker in the room with live waveform.
 */
@Composable
fun RiderRosterChip(
    name: String,
    isSelf: Boolean,
    isMuted: Boolean,
    amplitude: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF172336),
        border = BorderStroke(1.dp, if (isSelf) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF2A3D58))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color(0xFFFF1744) else Color(0xFF00E676))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMuted) {
                    Text("MUTED", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Black)
                } else {
                    AudioWaveVisualizer(
                        amplitude = amplitude,
                        isMuted = false,
                        barCount = 5,
                        maxHeight = 18.dp,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }
    }
}

/**
 * Audio Route and Volume Boost Tile.
 */
@Composable
fun AudioControlCard(
    currentRoute: AudioRouteType,
    isBluetoothConnected: Boolean,
    volumeBoost: Float,
    onCycleRoute: () -> Unit,
    onVolumeBoostChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF2A3B53))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Audio Output Destination", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = when (currentRoute) {
                            AudioRouteType.HELMET_BLUETOOTH -> "Bluetooth Helmet / Headset"
                            AudioRouteType.LOUDSPEAKER -> "Phone Loudspeaker"
                            AudioRouteType.EARPIECE -> "Phone Call Earpiece"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onCycleRoute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = when (currentRoute) {
                            AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                            AudioRouteType.LOUDSPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                            AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                        },
                        contentDescription = null,
                        tint = if (isBluetoothConnected) Color(0xFF00E676) else Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("SWITCH", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Volume Wind Boost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wind Noise Volume Boost", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${(volumeBoost * 100).toInt()}% (+${((volumeBoost - 1f) * 12).toInt()}dB)", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Slider(
                value = volumeBoost,
                onValueChange = onVolumeBoostChange,
                valueRange = 1.0f..4.0f,
                steps = 6,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2A42),
                    activeTrackColor = Color(0xFFFF2A42),
                    inactiveTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

/**
 * Floating Bottom Navigation Bar (Matching screenshot).
 */
@Composable
fun SportsBikeGlassNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = Color(0xFF0C101B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Home", Icons.Filled.Home, 0),
                Triple("Rides", Icons.Filled.TwoWheeler, 1),
                Triple("Garage", Icons.Filled.Build, 2),
                Triple("Security", Icons.Filled.Shield, 3),
                Triple("Profile", Icons.Filled.Person, 4)
            )

            tabs.forEach { (title, icon, index) ->
                val isSelected = selectedTab == index
                val color = if (isSelected) Color(0xFFFF2A42) else Color(0xFF64748B)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Dialog to edit and persist Rider Name.
 */
@Composable
fun RiderNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = {
            Text("Set Rider Name", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Your name will appear on the dashboard and in the room convoy list for other bikers.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF2A42),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
            ) {
                Text("SAVE NAME", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF94A3B8))
            }
        }
    )
}

/**
 * Dialog to customize Bike Model.
 */
@Composable
fun BikeModelDialog(
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentModel) }
    val popularBikes = listOf("Yamaha R15 V4", "Kawasaki Ninja ZX-6R", "BMW S1000RR", "Ducati Panigale V4", "KTM RC 390")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = {
            Text("Select / Customize Bike", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Bike Model") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF2A42),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Popular Bikes:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                popularBikes.forEach { bike ->
                    Text(
                        text = "• $bike",
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { text = bike }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
            ) {
                Text("SAVE BIKE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF94A3B8))
            }
        }
    )
}

/**
 * Dialog to switch or create Convoy Room.
 */
@Composable
fun RoomSwitchDialog(
    currentRoom: String,
    onDismiss: () -> Unit,
    onSelectRoom: (String) -> Unit
) {
    var roomInput by remember { mutableStateOf(currentRoom) }
    val presets = listOf("CONVOY 1", "CONVOY 2", "SPEED RUN", "WEEKEND TOUR")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = {
            Text("Switch Convoy Room", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "All bikers in the same room connect instantly over Hotspot and Nearby Mesh.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = roomInput,
                    onValueChange = { roomInput = it.uppercase() },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF2A42),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Preset Rooms:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                presets.forEach { preset ->
                    Text(
                        text = "• $preset",
                        color = Color(0xFF00E676),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { roomInput = preset }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectRoom(roomInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
            ) {
                Text("JOIN ROOM", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF94A3B8))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════
// ASTRARIDE "UNIVERSAL RIDER MESH" LAYOUT
// ═══════════════════════════════════════════════════════════════════

private val AstraBg = Color(0xFF0A0E17)
private val AstraBar = Color(0xFF111827)
private val AstraCard = Color(0xFF151C2A)
private val AstraCardInner = Color(0xFF1C2433)
private val AstraBorder = Color(0xFF2B3547)
private val AstraBlue = Color(0xFF38BDF8)
private val AstraGreen = Color(0xFF22C55E)
private val AstraMuted = Color(0xFF94A3B8)
private val AstraDim = Color(0xFF64748B)

@Composable
private fun AstraTopBar(
    currentRoute: AudioRouteType,
    isBluetoothConnected: Boolean,
    onCycleRoute: () -> Unit,
    unread: Int,
    onOpenChat: () -> Unit
) {
    Surface(color = AstraBar, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏍️", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AstraRide", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Universal Rider Mesh", color = AstraGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Box {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AstraCardInner).clickable(onClick = onOpenChat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Forum, contentDescription = "Mesh Chat", tint = AstraGreen, modifier = Modifier.size(22.dp))
                }
                if (unread > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(20.dp).clip(CircleShape).background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (unread > 9) "9+" else "$unread", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            val (label, icon) = when (currentRoute) {
                AudioRouteType.HELMET_BLUETOOTH -> "Helmet" to Icons.Filled.Headset
                AudioRouteType.LOUDSPEAKER -> "Speaker" to Icons.AutoMirrored.Filled.VolumeUp
                AudioRouteType.EARPIECE -> "Earpiece" to Icons.Filled.PhoneInTalk
            }
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCycleRoute),
                shape = RoundedCornerShape(14.dp),
                color = AstraCardInner
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = if (isBluetoothConnected) AstraGreen else AstraBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AstraPanel(
    borderColor: Color = AstraBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AstraCard),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun ConvoyRoomPanel(
    roomName: String,
    connectionState: MeshConnectionState,
    connectedRiders: List<com.bikeride.intercom.transport.local.nearby.ConnectedRider>,
    myRiderName: String,
    isMuted: Boolean,
    onSelectPreset: (String) -> Unit,
    onEditRoom: () -> Unit,
    myProfile: com.bikeride.intercom.mesh.RiderProfile,
    onEditProfile: () -> Unit
) {
    val presets = listOf("CONVOY 1", "CONVOY 2", "SQUAD ALPHA", "APEX RIDERS", "SPEED RUN", "WEEKEND TOUR")
    val statusDot = when (connectionState) {
        MeshConnectionState.CONNECTED -> AstraGreen
        MeshConnectionState.SEARCHING, MeshConnectionState.CONNECTING -> Color(0xFFFBBF24)
        else -> AstraDim
    }
    val subtitle = when (connectionState) {
        MeshConnectionState.CONNECTED -> "Live intercom · ${connectedRiders.size + 1} riders linked"
        MeshConnectionState.SEARCHING -> "Room open · scanning for riders…"
        MeshConnectionState.CONNECTING -> "Linking riders…"
        else -> "Tap 'TAP TO RIDE' to open room"
    }

    AstraPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(statusDot))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Convoy Room: $roomName",
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, color = AstraMuted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEditRoom),
                shape = RoundedCornerShape(12.dp),
                color = AstraCardInner
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = AstraBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Room", color = AstraBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                val selected = preset == roomName
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelectPreset(preset) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) AstraGreen.copy(alpha = 0.15f) else AstraCardInner,
                    border = if (selected) BorderStroke(1.5.dp, AstraGreen) else null
                ) {
                    Text(
                        preset,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        color = if (selected) AstraGreen else Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("CONNECTED RIDERS IN ROOM", color = AstraDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onEditProfile),
                shape = RoundedCornerShape(14.dp),
                color = AstraCardInner,
                border = BorderStroke(1.dp, Color(0xFF3B475C))
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    RiderAvatar(myProfile, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("$myRiderName (You)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (isMuted) "Host · Muted" else "Host · Tap to edit", color = AstraMuted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Edit, contentDescription = "Edit profile", tint = AstraBlue, modifier = Modifier.size(14.dp))
                }
            }
            connectedRiders.forEach { peer ->
                RiderPill(name = peer.name, role = peer.connectionType, isMuted = peer.isMuted)
            }
            if (connectedRiders.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AstraBorder)
                ) {
                    Text(
                        "Waiting for 2nd, 3rd biker to tap RIDE",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        color = AstraMuted, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RiderPill(name: String, role: String, isMuted: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AstraCardInner,
        border = BorderStroke(1.dp, Color(0xFF3B475C))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isMuted) Color(0xFFFF5252) else AstraBlue))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (isMuted) "$role · Muted" else role, color = AstraMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HandsFreeMutePanel(
    isMuted: Boolean,
    lastVoiceCommand: String?,
    onToggleMute: () -> Unit
) {
    AstraPanel(borderColor = Color(0xFF1E4F6E)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B3148)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PanTool, contentDescription = null, tint = AstraBlue, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Hands-Free Mute Control", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Wave glove or say 'Rider signing off'", color = AstraMuted, fontSize = 13.sp)
            }
            // Switch ON = mic live, OFF = muted
            Switch(
                checked = !isMuted,
                onCheckedChange = { onToggleMute() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AstraBlue,
                    uncheckedThumbColor = Color(0xFFCBD5E1),
                    uncheckedTrackColor = Color(0xFF7F1D1D)
                )
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111827)).padding(14.dp)
        ) {
            Text("👋  Wave glove 5cm over top of phone to Mute / Unmute", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("🎙️  Or speak: \"Rider signing off\" to Mute · \"Signing on\" to Unmute", color = AstraBlue, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "🛡️  " + (lastVoiceCommand?.let { "Last: $it" } ?: "Standalone in-app detection · Zero Gemini popups"),
                color = AstraDim, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isMuted) "● MIC MUTED" else "● MIC LIVE",
                color = if (isMuted) Color(0xFFFF5252) else AstraGreen,
                fontSize = 12.sp, fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun ChannelActivityPanel(
    isActive: Boolean,
    isMuted: Boolean,
    micAmplitude: Float,
    peerAmplitude: Float
) {
    val speaking = maxOf(if (isMuted) 0f else micAmplitude, peerAmplitude)
    val label = when {
        !isActive -> "CHANNEL QUIET"
        speaking > 0.05f -> "VOICE ACTIVE"
        else -> "CHANNEL OPEN"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AstraCard)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = AstraBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(20.dp))
            if (speaking > 0.05f) {
                AudioWaveVisualizer(
                    amplitude = speaking,
                    isMuted = false,
                    barCount = 13,
                    maxHeight = 28.dp,
                    modifier = Modifier.width(240.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(13) { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2DD4BF))) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TapToRideButton(
    isActive: Boolean,
    connectionState: MeshConnectionState,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "rideGlow")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow"
    )
    val accent = if (isActive) Color(0xFFEF4444) else Color(0xFF0EA5E9)
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(28.dp, CircleShape, spotColor = accent.copy(alpha = glow), ambientColor = accent.copy(alpha = glow))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0B1A2E))))
                .border(4.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isActive) "END RIDE" else "TAP TO RIDE",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
                Text(
                    when (connectionState) {
                        MeshConnectionState.CONNECTED -> "Intercom Live"
                        MeshConnectionState.SEARCHING -> "Searching Riders…"
                        MeshConnectionState.CONNECTING -> "Connecting…"
                        else -> "1-Click Intercom"
                    },
                    color = Color(0xFFCBD5E1), fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    riderName: String,
    bikeModel: String,
    onOpenHud: () -> Unit,
    onAlertHorn: () -> Unit,
    onEditName: () -> Unit,
    onEditBike: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionTile(Modifier.weight(1f), Icons.Filled.Timeline, "Live HUD", AstraBlue, onOpenHud)
        QuickActionTile(Modifier.weight(1f), Icons.Filled.Campaign, "Alert Horn", Color(0xFFFF9100), onAlertHorn)
        QuickActionTile(Modifier.weight(1f), Icons.Filled.Person, riderName, AstraGreen, onEditName)
        QuickActionTile(Modifier.weight(1f), Icons.Filled.TwoWheeler, bikeModel, Color(0xFFFF2A42), onEditBike)
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = AstraCard,
        border = BorderStroke(1.dp, AstraBorder)
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label, color = AstraMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IntercomGuideCard() {
    AstraPanel(borderColor = Color.Transparent) {
        Text("⚡ Multi-Biker Intercom Guide:", color = AstraGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(
            "1. Keep Room set to the same name (e.g. \"CONVOY 1\") on all phones.",
            "2. Tap 'TAP TO RIDE' on Phone 1, Phone 2, Phone 3, etc.",
            "3. They auto-link into full-duplex intercom!",
            "4. Say \"MUTE\" while riding to mute hands-free anytime."
        ).forEach {
            Text(it, color = Color(0xFFCBD5E1), fontSize = 14.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MeshChatCard(
    unread: Int,
    bluetoothLinks: Int,
    internetRelays: Int,
    reachableRiders: Int,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = AstraCard),
        border = BorderStroke(1.dp, Color(0xFF1F5135))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF14331F)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Forum, contentDescription = null, tint = AstraGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Convoy Mesh Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Messages, SOS & locations hop rider-to-rider — no internet needed",
                    color = AstraMuted, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "📶 $bluetoothLinks nearby · 👥 $reachableRiders riders · " + if (internetRelays > 0) "🌐 online" else "offline",
                    color = AstraBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (unread > 0) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (unread > 99) "99+" else "$unread", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
