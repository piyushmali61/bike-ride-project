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
    val lastVoiceCommand by viewModel.lastVoiceCommand.collectAsState()
    val riderName by viewModel.riderName.collectAsState()
    val bikeModel by viewModel.bikeModel.collectAsState()

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

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFF090D16),
        topBar = {
            // Sleek Top App Bar matching screenshot
            Surface(
                color = Color(0xFF0C101B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Menu Icon
                    IconButton(
                        onClick = { showRoomDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161F2E))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Greeting Column + Clickable Rider Name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showNameDialog = true }
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = greeting,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$riderName! ✌️",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Name",
                                tint = Color(0xFFFF2A42),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Notification Bell with Glowing Red Dot
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161F2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { viewModel.triggerEmergencyHorn() }) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // Glowing red unread badge
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-8).dp, y = 8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1744))
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Glassmorphism Floating Bottom Navigation Bar (Matching screenshot)
            SportsBikeGlassNavBar(
                selectedTab = selectedBottomTab,
                onTabSelected = { tab ->
                    selectedBottomTab = tab
                    when (tab) {
                        1 -> showRoomDialog = true
                        2 -> showBikeDialog = true
                        3 -> viewModel.triggerEmergencyHorn()
                        4 -> showNameDialog = true
                    }
                }
            )
        }
    ) { paddingValues ->

        val isSessionActive = connectionState == MeshConnectionState.CONNECTED || connectionState == MeshConnectionState.SEARCHING

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ═══════════════════════════════════════════════════════════
            // EMERGENCY HORN PULSING BANNER
            // ═══════════════════════════════════════════════════════════
            if (isEmergencyAlert) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744)),
                        elevation = CardDefaults.cardElevation(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "🚨 ALERT HORN SOUNDING! CONVOY SOS ACTIVE 🚨",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════
            // HERO SPORTS BIKE SHOWCASE CARD (GLASSMORPHISM)
            // ═══════════════════════════════════════════════════════════
            item {
                SportsBikeHeroCard(
                    bikeModel = bikeModel,
                    connectionState = connectionState,
                    latencyMs = latencyMs,
                    connectedCount = connectedRiders.size + 1,
                    onEditBike = { showBikeDialog = true }
                )
            }

            // ═══════════════════════════════════════════════════════════
            // CENTRAL ACTION BUTTONS (Exact Match to Screenshot 3 Circles!)
            // ═══════════════════════════════════════════════════════════
            item {
                CentralCockpitActionCluster(
                    isActive = isSessionActive,
                    onToggleRide = { viewModel.onOneClickConnectToggle() },
                    onOpenHud = { viewModel.toggleRidingHud(true) },
                    onAlertHorn = { viewModel.triggerEmergencyHorn() }
                )
            }

            // ═══════════════════════════════════════════════════════════
            // ZERO-TOUCH HANDS-FREE MUTE CONTROL CARD
            // ═══════════════════════════════════════════════════════════
            item {
                HandsFreeZeroTouchMuteCard(
                    isMuted = isMuted,
                    sensorStatus = lastVoiceCommand ?: "READY (WAVE OR DOUBLE-TAP)",
                    onToggleMute = { viewModel.toggleMute() }
                )
            }

            // ═══════════════════════════════════════════════════════════
            // CONVOY ROOM & ROUTE CARD (Matches "Last Ride" in screenshot)
            // ═══════════════════════════════════════════════════════════
            item {
                ConvoyRoomCard(
                    roomName = currentRoom,
                    connectedRiders = connectedRiders.values.toList(),
                    myRiderName = riderName,
                    isMuted = isMuted,
                    micAmplitude = micAmplitude,
                    peerAmplitude = peerAmplitude,
                    onSwitchRoom = { showRoomDialog = true }
                )
            }

            // ═══════════════════════════════════════════════════════════
            // AUDIO ROUTE & WIND BOOST SETTINGS
            // ═══════════════════════════════════════════════════════════
            item {
                AudioControlCard(
                    currentRoute = currentRoute,
                    isBluetoothConnected = isBluetoothConnected,
                    volumeBoost = volumeBoost,
                    onCycleRoute = { viewModel.cycleAudioRoute() },
                    onVolumeBoostChange = { viewModel.setVolumeBoost(it) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIALOGS: RIDER NAME, BIKE MODEL, ROOM SWITCH
    // ═══════════════════════════════════════════════════════════════════
    if (showNameDialog) {
        RiderNameDialog(
            currentName = riderName,
            onDismiss = { showNameDialog = false },
            onSave = { newName ->
                viewModel.setRiderName(newName)
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
