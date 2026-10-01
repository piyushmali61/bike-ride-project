package com.bikeride.intercom.feature.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.bluetooth.AudioRouteType
import com.bikeride.intercom.engine.audio.SpeechCommandSpotter
import com.bikeride.intercom.engine.audio.SpeechModelState
import com.bikeride.intercom.feature.ui.IntercomViewModel
import com.bikeride.intercom.feature.ui.R
import com.bikeride.intercom.feature.ui.components.AppearanceSection
import com.bikeride.intercom.feature.ui.components.AudioWaveVisualizer
import com.bikeride.intercom.feature.ui.components.ChannelQuietCard
import com.bikeride.intercom.feature.ui.components.RidingHudOverlay
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.RiderProfile
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState
import kotlinx.coroutines.launch

/**
 * AstraRide - Universal Rider Mesh Cockpit UI.
 *
 * Visually matches the high-contrast cockpit dashboard:
 * - Twilight mountain road background wallpaper with high-contrast gradient
 * - Convoy Room card with Room & Delete options and active rider indicators
 * - Horizontal Connected Riders row with Host crown, online badges, and invite button
 * - Convoy Mesh Chat status card with nearby, online, and reachability metrics
 * - Hands-Free Control card with toggle switch and Wave / Double Tap / Voice SOS pills
 * - Channel Quiet card with live equalizer meter
 * - Central giant glowing neon-cyan circular "TAP TO RIDE" button with concentric pulse rings
 * - Cockpit satellite buttons: Live HUD, Alert Horn, Rider Profile, Bike Model
 * - Audio Output Destination card with route switch button and wind noise boost slider
 * - Multi-Biker Intercom Guide card with 4-step setup
 * - Floating bottom glass navigation dock (Home, Convoy, big center floating glowing red SOS button, Riders, Settings)
 * - Complete, robust delete architecture for Convoy, Bike, and Rider data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: IntercomViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onSettings: () -> Unit = {},
    onRidingMode: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onPictureStop: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    val riderName by viewModel.riderName.collectAsState()
    val bikeModel by viewModel.bikeModel.collectAsState()
    val selectedRoom by viewModel.customRideCode.collectAsState()
    val myRooms by viewModel.myRooms.collectAsState()
    var showSosConfirm by remember { mutableStateOf(false) }
    var showHudChooser by remember { mutableStateOf(false) }
    val riderProfile by viewModel.riderProfile.collectAsState()
    val meshUnread by viewModel.meshUnread.collectAsState()
    val meshBtLinks by viewModel.meshBluetoothLinks.collectAsState()
    val meshRelays by viewModel.meshInternetRelays.collectAsState()
    val meshPeers by viewModel.meshPeers.collectAsState()
    val voiceSosState by viewModel.voiceSosState.collectAsState()
    val voiceSosEnabled by viewModel.voiceSosEnabled.collectAsState()

    var showVoiceSosSetup by remember { mutableStateOf(false) }
    val onToggleVoiceSos: () -> Unit = {
        when (voiceSosState) {
            SpeechModelState.NotInstalled, is SpeechModelState.Failed -> showVoiceSosSetup = true
            is SpeechModelState.Downloading -> Unit
            SpeechModelState.Ready -> viewModel.setVoiceSos(!voiceSosEnabled)
        }
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showBikeDialog by remember { mutableStateOf(false) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDeleteConvoyConfirm by remember { mutableStateOf(false) }
    var showDeleteBikeConfirm by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }

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

    // Full-Screen OLED Handlebar HUD
    if (isRidingHudOpen) {
        RidingHudOverlay(
            isActive = connectionState != MeshConnectionState.IDLE && connectionState != MeshConnectionState.DISCONNECTED,
            // Room of the running ride, or the selected room before a ride starts
            roomName = if (connectionState == MeshConnectionState.IDLE || connectionState == MeshConnectionState.DISCONNECTED) selectedRoom else currentRoom,
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
            isEmergencyAlert = isEmergencyAlert,
            quickAlerts = IntercomViewModel.QUICK_ALERTS,
            onQuickAlert = { viewModel.sendQuickAlert(it) },
            onShareLocation = { viewModel.shareMyLocation() },
            voiceSosLabel = if (voiceSosEnabled) "Voice SOS: ON" else "Voice SOS: off",
            voiceSosOn = voiceSosEnabled,
            onToggleVoiceSos = onToggleVoiceSos
        )
        if (showVoiceSosSetup) {
            VoiceSosSetupDialog(
                onDismiss = { showVoiceSosSetup = false },
                onConfirm = { viewModel.setVoiceSos(true); showVoiceSosSetup = false }
            )
        }
        return
    }

    val isRideActive = connectionState == MeshConnectionState.CONNECTED ||
        connectionState == MeshConnectionState.SEARCHING ||
        connectionState == MeshConnectionState.CONNECTING

    // Background Container with Cockpit Mountain Highway Wallpaper & Dark Gradient Overlay
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_cockpit_highway),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF090D16).copy(alpha = 0.60f),
                            Color(0xFF070B13).copy(alpha = 0.86f),
                            Color(0xFF04060A).copy(alpha = 0.98f)
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AstraTopBar(
                    currentRoute = currentRoute,
                    isBluetoothConnected = isBluetoothConnected,
                    onCycleRoute = { viewModel.cycleAudioRoute() },
                    onOpenSettings = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                AstraBottomBar(
                    selectedTab = selectedBottomTab,
                    onTabSelected = { tab ->
                        selectedBottomTab = tab
                        when (tab) {
                            0 -> Unit // Home
                            1 -> onOpenChat() // Convoy Chat
                            3 -> showNameDialog = true // Riders
                            4 -> showSettingsDialog = true // Settings
                        }
                    },
                    // Confirm first: a bump or glove brushing the bar must not alarm the whole convoy.
                    // (triggerEmergencyHorn already sends the SOS; calling sendSos too sent it twice.)
                    onSosClick = { showSosConfirm = true }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(2.dp)) }

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

                // 1. Convoy Room Panel (with Room switch and Delete options)
                item {
                    ConvoyRoomPanel(
                        roomName = selectedRoom,
                        connectionState = connectionState,
                        connectedRiders = connectedRiders.values.toList(),
                        onSelectPreset = { viewModel.setCustomRideCode(it) },
                        onEditRoom = { showRoomDialog = true },
                        onDeleteConvoy = { showDeleteConvoyConfirm = true },
                        myRooms = myRooms
                    )
                }

                // 2. Connected Riders Section (you as host, real connected riders, + Invite)
                item {
                    ConnectedRidersSection(
                        myRiderName = riderName,
                        myProfile = riderProfile,
                        connectedRiders = connectedRiders.values.toList(),
                        onEditProfile = { showNameDialog = true },
                        onInvite = { showRoomDialog = true }
                    )
                }

                // 3. Convoy Mesh Chat Card (0 nearby, 3 riders, Online)
                item {
                    ConvoyMeshChatCard(
                        unread = meshUnread,
                        bluetoothLinks = meshBtLinks,
                        reachableRiders = meshPeers.values.count {
                            System.currentTimeMillis() - it.lastSeen < ConvoyMesh.PEER_ACTIVE_MS
                        }.coerceAtLeast(connectedRiders.size + 1),
                        isOnline = meshRelays > 0,
                        onOpen = onOpenChat
                    )
                }

                // 4. Hands-Free Control Card (Wave, Double Tap, Voice SOS)
                item {
                    HandsFreeControlCard(
                        isMuted = isMuted,
                        onToggleMute = { viewModel.toggleMute() },
                        voiceSosEnabled = voiceSosEnabled,
                        onToggleVoiceSos = onToggleVoiceSos
                    )
                }

                // 5. CHANNEL QUIET / Voice Activity Panel
                item {
                    ChannelQuietCard(
                        isActive = isRideActive,
                        isMuted = isMuted,
                        amplitude = if (micAmplitude > 0.05f) micAmplitude else peerAmplitude
                    )
                }

                // 6. Central Cockpit Dial Cluster (TAP TO RIDE with 4 Satellites: HUD, Horn, Rider, Bike)
                item {
                    CockpitRadialActionCluster(
                        isActive = isRideActive,
                        connectionState = connectionState,
                        bikeModel = bikeModel,
                        onToggleRide = { viewModel.onOneClickConnectToggle() },
                        onOpenHud = { showHudChooser = true },
                        onAlertHorn = {
                            viewModel.triggerEmergencyHorn()
                            Toast.makeText(context, "Sounding Alert Horn!", Toast.LENGTH_SHORT).show()
                        },
                        onOpenRider = { showNameDialog = true },
                        onOpenBike = { showBikeDialog = true }
                    )
                }

                // 7. Audio Output Destination Card
                item {
                    AudioOutputCard(
                        currentRoute = currentRoute,
                        isBluetoothConnected = isBluetoothConnected,
                        volumeBoost = volumeBoost,
                        onCycleRoute = { viewModel.cycleAudioRoute() },
                        onVolumeBoostChange = { viewModel.setVolumeBoost(it) }
                    )
                }

                // 8. Multi-Biker Intercom Guide Card
                item {
                    IntercomGuideCard()
                }

                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIALOGS & DELETE CONFIRMATION ARCHITECTURE
    // ═══════════════════════════════════════════════════════════════════

    // Rider & Bike Profile Dialog
    if (showNameDialog) {
        ProfileDialog(
            current = riderProfile,
            onDismiss = { showNameDialog = false },
            onSave = { updated ->
                viewModel.updateProfile(updated)
                showNameDialog = false
            },
            onDeleteBike = {
                coroutineScope.launch {
                    val success = viewModel.deleteBikeData()
                    if (success) {
                        snackbarHostState.showSnackbar("Item deleted successfully.")
                    } else {
                        snackbarHostState.showSnackbar("Failed to delete item.")
                    }
                }
            },
            onDeleteRider = {
                coroutineScope.launch {
                    val success = viewModel.deleteRiderData()
                    if (success) {
                        snackbarHostState.showSnackbar("Item deleted successfully.")
                    } else {
                        snackbarHostState.showSnackbar("Failed to delete item.")
                    }
                }
            }
        )
    }

    // Bike Model Dialog
    if (showBikeDialog) {
        BikeModelDialog(
            currentModel = bikeModel,
            onDismiss = { showBikeDialog = false },
            onSave = { newModel ->
                viewModel.setBikeModel(newModel)
                showBikeDialog = false
            },
            onDeleteBike = {
                showBikeDialog = false
                showDeleteBikeConfirm = true
            }
        )
    }

    // Voice SOS Setup Dialog
    if (showHudChooser) {
        LiveHudChooserDialog(
            onDismiss = { showHudChooser = false },
            onWithMap = { showHudChooser = false; onOpenMap() },
            onHudOnly = { showHudChooser = false; viewModel.toggleRidingHud(true) }
        )
    }

    if (showSosConfirm) {
        AlertDialog(
            onDismissRequest = { showSosConfirm = false },
            containerColor = Color(0xFF131D2D),
            title = { Text("Send SOS to the convoy?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Every rider in $selectedRoom hears an alarm and gets your location.",
                    color = Color(0xFFCBD5E1), fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosConfirm = false
                        viewModel.triggerEmergencyHorn()
                        Toast.makeText(context, "Convoy SOS broadcast sent!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("SEND SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirm = false }) { Text("CANCEL", color = Color(0xFF94A3B8)) }
            }
        )
    }

    if (showVoiceSosSetup) {
        VoiceSosSetupDialog(
            onDismiss = { showVoiceSosSetup = false },
            onConfirm = { viewModel.setVoiceSos(true); showVoiceSosSetup = false }
        )
    }

    // Room Switch Dialog
    if (showRoomDialog) {
        RoomSwitchDialog(
            currentRoom = selectedRoom,
            onDismiss = { showRoomDialog = false },
            onSelectRoom = { newRoom ->
                viewModel.setCustomRideCode(newRoom)
                showRoomDialog = false
            },
            myRooms = myRooms,
            onAddRoom = { viewModel.addMyRoom(it) },
            onRemoveMyRoom = { viewModel.removeMyRoom(it) },
            onDeleteRoom = { roomToDelete ->
                coroutineScope.launch {
                    val success = viewModel.deleteConvoy(roomToDelete)
                    if (success) {
                        snackbarHostState.showSnackbar("Item deleted successfully.")
                    } else {
                        snackbarHostState.showSnackbar("Failed to delete item.")
                    }
                }
            }
        )
    }

    // Settings & Appearance (Color Customization) Modal
    if (showSettingsDialog) {
        AstraSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Delete Convoy Confirmation Dialog
    if (showDeleteConvoyConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConvoyConfirm = false },
            containerColor = Color(0xFF131D2D),
            title = {
                Text("Delete this item?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete all stored chat, markers, and cached mesh data for convoy \"$selectedRoom\"?\n\nThis cannot be undone.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConvoyConfirm = false
                        coroutineScope.launch {
                            val success = viewModel.deleteConvoy(selectedRoom)
                            if (success) {
                                snackbarHostState.showSnackbar("Item deleted successfully.")
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete item.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConvoyConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Bike Confirmation Dialog
    if (showDeleteBikeConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteBikeConfirm = false },
            containerColor = Color(0xFF131D2D),
            title = {
                Text("Delete this item?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Text(
                    "Are you sure you want to delete all stored bike profile data and restore default bike settings?\n\nThis action cannot be undone.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBikeConfirm = false
                        coroutineScope.launch {
                            val success = viewModel.deleteBikeData()
                            if (success) {
                                snackbarHostState.showSnackbar("Item deleted successfully.")
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete item.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBikeConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AstraTopBar(
    currentRoute: AudioRouteType,
    isBluetoothConnected: Boolean,
    onCycleRoute: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = Color(0xFF0C101B).copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stylized motorcycle icon with cyan glow
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0C243B))
                    .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.TwoWheeler,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AstraRide",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Universal Rider Mesh",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Speaker Pill Button
            val (label, icon) = when (currentRoute) {
                AudioRouteType.HELMET_BLUETOOTH -> "Helmet" to Icons.Filled.Headset
                AudioRouteType.LOUDSPEAKER -> "Speaker" to Icons.AutoMirrored.Filled.VolumeUp
                AudioRouteType.EARPIECE -> "Earpiece" to Icons.Filled.PhoneInTalk
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCycleRoute),
                shape = RoundedCornerShape(50),
                color = Color(0xFF131D2D),
                border = BorderStroke(1.dp, Color(0xFF2A3D58))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = if (isBluetoothConnected) Color(0xFF00E676) else Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Settings Gear Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF131D2D))
                    .border(1.dp, Color(0xFF2A3D58), CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 1. CONVOY ROOM PANEL
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ConvoyRoomPanel(
    roomName: String,
    connectionState: MeshConnectionState,
    connectedRiders: List<com.bikeride.intercom.transport.local.nearby.ConnectedRider>,
    onSelectPreset: (String) -> Unit,
    onEditRoom: () -> Unit,
    onDeleteConvoy: () -> Unit,
    myRooms: List<String> = emptyList()
) {
    // Rider counts are only known for the room we are in, so other chips show no number
    val presets = (listOf("CONVOY 1", "CONVOY 2", "SQUAD ALPHA") + myRooms).distinct().map { it to 0 }
    val statusDot = when (connectionState) {
        MeshConnectionState.CONNECTED -> Color(0xFF00E676)
        MeshConnectionState.SEARCHING, MeshConnectionState.CONNECTING -> Color(0xFFFBBF24)
        else -> Color(0xFF00E676)
    }
    val subtitle = when (connectionState) {
        MeshConnectionState.CONNECTED -> "Live intercom active"
        MeshConnectionState.SEARCHING -> "Room open · scanning for riders…"
        MeshConnectionState.CONNECTING -> "Linking riders…"
        else -> "Tap 'TAP TO RIDE' to open room"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF2A3D58))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Green icon rounded square
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F3B2E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Convoy Room",
                        color = Color.White,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusDot)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${maxOf(1, connectedRiders.size + 1)} riders connected",
                            color = Color(0xFF00E676),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Action buttons: Room (blue) and Delete (red)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onEditRoom),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C2A44),
                        border = BorderStroke(1.dp, Color(0xFF1E507A))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Room", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDeleteConvoy),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF3A141A),
                        border = BorderStroke(1.dp, Color(0xFF7A1E26))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Convoy", tint = Color(0xFFFF2A42), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = Color(0xFFFF2A42), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Room chips: built-in rooms + rooms the rider saved; live count on the current room
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allPresets = if (presets.none { it.first.equals(roomName, ignoreCase = true) }) {
                    listOf(roomName to (connectedRiders.size + 1)) + presets
                } else {
                    presets
                }

                allPresets.forEach { (preset, defaultCount) ->
                    val isSelected = preset.equals(roomName, ignoreCase = true)
                    val count = if (isSelected) maxOf(1, connectedRiders.size + 1) else defaultCount
                    val showCount = isSelected

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectPreset(preset) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) Color(0xFF0C3B2A) else Color(0xFF172336),
                        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF00E676)) else BorderStroke(1.dp, Color(0xFF24354D))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = preset,
                                color = if (isSelected) Color(0xFF00E676) else Color(0xFFCBD5E1),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (showCount) Spacer(Modifier.width(8.dp))
                            if (showCount) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF00E676) else Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "$count",
                                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF64748B),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 2. CONNECTED RIDERS IN ROOM
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ConnectedRidersSection(
    myRiderName: String,
    myProfile: RiderProfile,
    connectedRiders: List<com.bikeride.intercom.transport.local.nearby.ConnectedRider>,
    onEditProfile: () -> Unit,
    onInvite: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONNECTED RIDERS IN ROOM",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = if (connectedRiders.isEmpty()) "Waiting for 2nd rider..." else "${connectedRiders.size + 1} riders active",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rider (You) - Host
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onEditProfile)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF132035))
                            .border(2.dp, Color(0xFF00E5FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        RiderAvatar(myProfile, size = 44)
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                            .border(1.5.dp, Color(0xFF090D16), CircleShape)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = 10.sp)
                    Spacer(Modifier.width(2.dp))
                    Text("$myRiderName (You)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text("Host", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            // Only real connected riders — never placeholder names, or riders think someone is listening
            val displayPeers = connectedRiders.map { it.name }

            displayPeers.forEach { peerName ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF172336))
                                .border(1.5.dp, Color(0xFF2A3D58), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏍️", fontSize = 24.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                                .border(1.5.dp, Color(0xFF090D16), CircleShape)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(peerName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Rider", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
            }

            // + Invite Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onInvite)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF131D2D).copy(alpha = 0.6f))
                        .border(1.5.dp, Color(0xFF2A3D58), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Invite",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Invite", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 3. CONVOY MESH CHAT CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ConvoyMeshChatCard(
    unread: Int,
    bluetoothLinks: Int,
    reachableRiders: Int,
    isOnline: Boolean,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF1F5135))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF14331F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Forum, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Convoy Mesh Chat", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    }
                    Text(
                        "Messages, SOS & locations • rider-to-rider (no internet needed)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Badges row: 0 nearby, 3 riders, Online
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$bluetoothLinks nearby", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$reachableRiders riders", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isOnline) "Online" else "Mesh Only", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 4. HANDS-FREE CONTROL CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HandsFreeControlCard(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    voiceSosEnabled: Boolean,
    onToggleVoiceSos: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF1E354F))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B3148)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PanTool, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hands-Free Control", color = Color.White, fontSize = 17.5.sp, fontWeight = FontWeight.Bold)
                    Text("Wave, double-tap or speak • no touching needed", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Switch(
                    checked = !isMuted,
                    onCheckedChange = { onToggleMute() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E676),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            // 3 Action Pills: Wave, Double Tap, Voice SOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleMute),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F253C),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👋", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Wave", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("5 cm over phone", color = Color(0xFF38BDF8), fontSize = 10.5.sp)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleMute),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF172336),
                    border = BorderStroke(1.dp, Color(0xFF2A3D58))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Double Tap", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Riding screen", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleVoiceSos),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF172336),
                    border = BorderStroke(1.dp, Color(0xFF2A3D58))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Voice SOS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Say \"SOS\" twice", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 5. CHANNEL QUIET CARD
// ═══════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════
// 6. CENTRAL RADIAL COCKPIT ACTION CLUSTER
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CockpitRadialActionCluster(
    isActive: Boolean,
    connectionState: MeshConnectionState,
    bikeModel: String,
    onToggleRide: () -> Unit,
    onOpenHud: () -> Unit,
    onAlertHorn: () -> Unit,
    onOpenRider: () -> Unit,
    onOpenBike: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CockpitSatelliteButton(
                    icon = Icons.Filled.AltRoute,
                    label = "Live HUD",
                    tint = Color(0xFF00E5FF),
                    onClick = onOpenHud
                )
                CockpitSatelliteButton(
                    icon = Icons.Filled.Campaign,
                    label = "Alert Horn",
                    tint = Color(0xFFFF9100),
                    onClick = onAlertHorn
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CockpitSatelliteButton(
                    icon = Icons.Filled.Person,
                    label = "Rider",
                    tint = Color(0xFF00E676),
                    onClick = onOpenRider
                )
                CockpitSatelliteButton(
                    icon = Icons.Filled.TwoWheeler,
                    label = "$bikeModel >",
                    tint = Color(0xFFFF2A42),
                    onClick = onOpenBike
                )
            }
        }

        TapToRideCenterButton(
            isActive = isActive,
            connectionState = connectionState,
            onClick = onToggleRide
        )
    }
}

@Composable
private fun TapToRideCenterButton(
    isActive: Boolean,
    connectionState: MeshConnectionState,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val cyanAccent = Color(0xFF00E5FF)
    val redAccent = Color(0xFFFF1744)
    val accent = if (isActive) redAccent else cyanAccent

    Box(
        modifier = Modifier
            .size(200.dp)
            .drawBehind {
                drawCircle(
                    color = accent.copy(alpha = glow * 0.35f),
                    radius = (size.minDimension / 2f) * pulse,
                    style = Stroke(width = 5.dp.toPx())
                )
                drawCircle(
                    color = accent.copy(alpha = glow * 0.7f),
                    radius = (size.minDimension / 2f) * 0.92f,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(165.dp)
                .shadow(24.dp, CircleShape, spotColor = accent.copy(alpha = glow), ambientColor = accent.copy(alpha = glow))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = 0.45f),
                            Color(0xFF091C2E).copy(alpha = 0.95f),
                            Color(0xFF06101D)
                        )
                    )
                )
                .border(3.5.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isActive) "END RIDE" else "TAP TO RIDE",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (connectionState) {
                        MeshConnectionState.CONNECTED -> "Intercom Live"
                        MeshConnectionState.SEARCHING -> "Searching..."
                        MeshConnectionState.CONNECTING -> "Connecting..."
                        else -> "1-Click Intercom"
                    },
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CockpitSatelliteButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFF131D2D).copy(alpha = 0.92f))
                .border(1.5.dp, tint.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = Color(0xFFCBD5E1),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// 7. AUDIO OUTPUT DESTINATION CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AudioOutputCard(
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F2B3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (currentRoute) {
                                AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                                AudioRouteType.LOUDSPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                                AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                            },
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
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
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCycleRoute),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SWITCH", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
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
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 8. MULTI-BIKER INTERCOM GUIDE CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun IntercomGuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⚡", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Multi-Biker Intercom Guide",
                    color = Color(0xFF00E676),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    GuideStepItem("1", "Keep same room name (e.g. 'CONVOY 1') on all phones")
                    Spacer(Modifier.height(10.dp))
                    GuideStepItem("2", "Tap TAP TO RIDE on Phone 1, 2, 3, etc.")
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    GuideStepItem("3", "They auto-link into full-duplex intercom")
                    Spacer(Modifier.height(10.dp))
                    GuideStepItem("4", "Double-tap the riding screen to mute · say \"SOS\" twice for help")
                }
            }
        }
    }
}

@Composable
private fun GuideStepItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C3857)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color(0xFF38BDF8), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color(0xFFCBD5E1), fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

// ═══════════════════════════════════════════════════════════════════
// FLOATING BOTTOM NAVIGATION BAR
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AstraBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSosClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF0C121E).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color(0xFF223147)),
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 0: Home
            BottomNavItem(
                icon = Icons.Filled.Home,
                label = "Home",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )

            // 1: Convoy
            BottomNavItem(
                icon = Icons.Filled.Groups,
                label = "Convoy",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )

            // Center Floating SOS Button
            FloatingSosButton(onClick = onSosClick)

            // 3: Riders
            BottomNavItem(
                icon = Icons.Filled.People,
                label = "Riders",
                isSelected = selectedTab == 3,
                onClick = { onTabSelected(3) }
            )

            // 4: Settings
            BottomNavItem(
                icon = Icons.Filled.Settings,
                label = "Settings",
                isSelected = selectedTab == 4,
                onClick = { onTabSelected(4) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF00E5FF))
            )
        } else {
            Spacer(Modifier.height(2.5.dp))
        }
    }
}

@Composable
private fun FloatingSosButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // Sits high in the bar (fully visible) with a clear gap above the bottom border
            .offset(y = (-8).dp)
            .size(54.dp)
            .shadow(16.dp, CircleShape, spotColor = Color(0xFFFF1744), ambientColor = Color(0xFFFF2A42))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFFFF2A42),
                        Color(0xFFB71C1C)
                    )
                )
            )
            .border(2.5.dp, Color(0xFFFF8A80), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "SOS",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = "SOS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// DIALOGS: BIKE MODEL, ROOM SWITCH, VOICE SOS, SETTINGS
// ═══════════════════════════════════════════════════════════════════

@Composable
fun BikeModelDialog(
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDeleteBike: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(currentModel) }
    val popularBikes = listOf(
        "CB Hornet 125",
        "Hunter 350",
        "Yamaha R15 V4",
        "KTM RC 390",
        "Kawasaki Ninja ZX-6R",
        "BMW S1000RR",
        "Ducati Panigale V4"
    )

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
                if (onDeleteBike != null) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onDeleteBike,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF2A42)),
                        border = BorderStroke(1.dp, Color(0xFFFF2A42).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Bike Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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

@Composable
fun RoomSwitchDialog(
    currentRoom: String,
    onDismiss: () -> Unit,
    onSelectRoom: (String) -> Unit,
    myRooms: List<String> = emptyList(),
    onAddRoom: ((String) -> Unit)? = null,
    onRemoveMyRoom: ((String) -> Unit)? = null,
    onDeleteRoom: ((String) -> Unit)? = null
) {
    var roomInput by remember { mutableStateOf(currentRoom) }
    var roomToDeleteConfirm by remember { mutableStateOf<String?>(null) }
    val presets = com.bikeride.intercom.feature.ui.IntercomViewModel.DEFAULT_ROOMS
    val canAdd = onAddRoom != null && roomInput.isNotBlank() &&
        roomInput.trim() !in presets && roomInput.trim() !in myRooms

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(Modifier.width(8.dp))
                Text("Switch Convoy Room", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "All bikers in the same room connect instantly over Hotspot and Nearby Mesh.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it.uppercase().take(24) },
                        singleLine = true,
                        placeholder = { Text("Room name", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFF2A42),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (onAddRoom != null) {
                        Spacer(Modifier.width(8.dp))
                        // Save the typed name as one of "My Rooms"
                        Button(
                            onClick = { onAddRoom(roomInput.trim()) },
                            enabled = canAdd,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0EA5E9),
                                disabledContainerColor = Color(0xFF1E293B)
                            )
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("ADD", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (myRooms.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("My Rooms:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    myRooms.forEach { room ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $room",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f).clickable { roomInput = room }.padding(vertical = 4.dp)
                            )
                            if (onRemoveMyRoom != null) {
                                IconButton(onClick = { onRemoveMyRoom(room) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove $room", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Preset Rooms:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• $preset",
                            color = Color(0xFF00E676),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { roomInput = preset }
                                .padding(vertical = 4.dp)
                        )
                        if (onDeleteRoom != null) {
                            IconButton(
                                onClick = { roomToDeleteConfirm = preset },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete $preset data",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
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

    roomToDeleteConfirm?.let { preset ->
        AlertDialog(
            onDismissRequest = { roomToDeleteConfirm = null },
            containerColor = Color(0xFF131D2D),
            title = {
                Text("Delete this item?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete stored data for room '$preset'? This cannot be undone.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = preset
                        roomToDeleteConfirm = null
                        onDeleteRoom?.invoke(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToDeleteConfirm = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun VoiceSosSetupDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151C2A),
        title = { Text("Set up Voice SOS", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Say \"SOS\" twice while riding and the whole convoy hears an alarm with your location.\n\n" +
                    "This downloads a ${SpeechCommandSpotter.MODEL_SIZE_MB} MB offline speech model once (use Wi-Fi). " +
                    "After that it works with no internet, and nothing you say leaves the phone.",
                color = Color(0xFF94A3B8)
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                Text("DOWNLOAD", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("NOT NOW", color = Color(0xFF94A3B8)) } }
    )
}

@Composable
private fun AstraSettingsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF00E5FF))
                Spacer(Modifier.width(8.dp))
                Text("Settings & Customization", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                AppearanceSection()
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("DONE", color = Color(0xFF090D16), fontWeight = FontWeight.Black)
            }
        }
    )
}

/** Live HUD: choose the map view or the buttons-only riding screen. */
@Composable
private fun LiveHudChooserDialog(onDismiss: () -> Unit, onWithMap: () -> Unit, onHudOnly: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131D2D),
        title = { Text("Open Live HUD", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HudChoice(Icons.Filled.Map, "Live HUD with Map", "Navigation map with ride controls", Color(0xFF00E5FF), onWithMap)
                HudChoice(Icons.Filled.TouchApp, "Live HUD only", "Big ride buttons, no map", Color(0xFF00E676), onHudOnly)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF94A3B8)) } }
    )
}

@Composable
private fun HudChoice(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF172336),
        border = BorderStroke(1.5.dp, tint.copy(alpha = 0.6f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 13.sp)
            }
        }
    }
}
