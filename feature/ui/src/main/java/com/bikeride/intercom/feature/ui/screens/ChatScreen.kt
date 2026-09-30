package com.bikeride.intercom.feature.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bikeride.intercom.feature.ui.ChatViewModel
import com.bikeride.intercom.mesh.ChatMessage
import com.bikeride.intercom.mesh.ConvoyMesh
import com.bikeride.intercom.mesh.DeliveryState
import com.bikeride.intercom.mesh.MeshType
import com.bikeride.intercom.mesh.RiderProfile
import com.bikeride.intercom.mesh.Via
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChatBg = Color(0xFF0A0E17)
private val ChatBar = Color(0xFF111827)
private val ChatCard = Color(0xFF151C2A)
private val ChatInner = Color(0xFF1C2433)
private val ChatBlue = Color(0xFF38BDF8)
private val ChatGreen = Color(0xFF22C55E)
private val ChatMuted = Color(0xFF94A3B8)
private val ChatDim = Color(0xFF64748B)
private val SosRed = Color(0xFFEF4444)

/**
 * Convoy Mesh Chat: messages hop phone-to-phone over Bluetooth with no internet,
 * and also travel over public relays whenever any rider has data.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val room by viewModel.room.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val btLinks by viewModel.bluetoothLinks.collectAsState()
    val btRunning by viewModel.bluetoothRunning.collectAsState()
    val relays by viewModel.internetRelays.collectAsState()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    var confirmSos by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        viewModel.onVisible(true)
        onDispose { viewModel.onVisible(false) }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val now = System.currentTimeMillis()
    val activePeers = peers.values.filter { now - it.lastSeen < ConvoyMesh.PEER_ACTIVE_MS }

    Scaffold(
        containerColor = ChatBg,
        topBar = {
            Surface(color = ChatBar) {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Mesh Chat · $room", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${activePeers.size} rider${if (activePeers.size == 1) "" else "s"} reachable · works without internet",
                                color = ChatGreen, fontSize = 12.sp
                            )
                        }
                    }
                    Row(Modifier.padding(start = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinkPill(
                            icon = { Icon(Icons.Filled.Bluetooth, null, tint = if (btLinks > 0) ChatBlue else ChatDim, modifier = Modifier.size(14.dp)) },
                            text = when {
                                !btRunning -> "Bluetooth off · tap to retry"
                                btLinks == 0 -> "Scanning nearby…"
                                else -> "$btLinks nearby link${if (btLinks == 1) "" else "s"}"
                            },
                            active = btLinks > 0,
                            onClick = if (!btRunning) ({ viewModel.retryBluetooth() }) else null
                        )
                        LinkPill(
                            icon = {
                                Icon(if (relays > 0) Icons.Filled.Cloud else Icons.Filled.CloudOff, null, tint = if (relays > 0) ChatGreen else ChatDim, modifier = Modifier.size(14.dp))
                            },
                            text = if (relays > 0) "Internet sync on" else "Offline mode",
                            active = relays > 0,
                            onClick = null
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = ChatBar) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickChip("🆘 SOS", SosRed) { confirmSos = true }
                        QuickChip("📍 Location", ChatBlue) {
                            if (!viewModel.shareLocation()) {
                                Toast.makeText(context, "No location yet — turn on GPS and try again", Toast.LENGTH_SHORT).show()
                            }
                        }
                        listOf("⛽ Fuel stop", "☕ Break", "🐢 Slow down", "✅ All good", "🔧 Bike trouble", "🏁 Reached").forEach { q ->
                            QuickChip(q, ChatMuted) { viewModel.send(q) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.take(ConvoyMesh.MAX_CHAT_CHARS) },
                            placeholder = { Text("Message the convoy…", color = ChatDim) },
                            maxLines = 3,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = ChatBlue,
                                unfocusedBorderColor = Color(0xFF2B3547),
                                focusedContainerColor = ChatInner,
                                unfocusedContainerColor = ChatInner
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (input.isBlank()) ChatInner else Color(0xFF0EA5E9))
                                .clickable(enabled = input.isNotBlank()) {
                                    viewModel.send(input)
                                    input = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Riders reachable right now
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PeerBadge(profile, "You")
                activePeers.sortedBy { it.hops }.forEach { peer ->
                    PeerBadge(
                        peer.profile,
                        when (peer.via) {
                            Via.INTERNET -> "Internet"
                            else -> if (peer.hops <= 1) "Direct" else "${peer.hops} hops"
                        }
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF1F2937))

            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📡", fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No messages yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Messages hop rider-to-rider over Bluetooth, so the convoy stays in touch even with no signal. " +
                            "They are stored and delivered when riders come back in range.",
                        color = ChatMuted, fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.key }) { msg ->
                        MessageBubble(
                            msg = msg,
                            senderProfile = peers[msg.senderId]?.profile,
                            myProfile = profile,
                            onOpenMap = { lat, lon ->
                                val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(msg.senderName)})")
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            containerColor = ChatCard,
            title = { Text("Send SOS to the convoy?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Every rider in $room hears an alarm and gets your last known location, relayed through other phones if needed.",
                    color = ChatMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendSos(); confirmSos = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed)
                ) { Text("SEND SOS", color = Color.White, fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("CANCEL", color = ChatMuted) } }
        )
    }
}

@Composable
private fun LinkPill(icon: @Composable () -> Unit, text: String, active: Boolean, onClick: (() -> Unit)?) {
    Surface(
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(50),
        color = if (active) ChatInner else Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF2B3547))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(5.dp))
            Text(text, color = if (active) Color.White else ChatMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickChip(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PeerBadge(profile: RiderProfile, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        RiderAvatar(profile, size = 44)
        Spacer(Modifier.height(4.dp))
        Text(profile.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = ChatDim, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    senderProfile: RiderProfile?,
    myProfile: RiderProfile,
    onOpenMap: (Double, Double) -> Unit
) {
    val time = remember(msg.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)) }
    val isSos = msg.type == MeshType.SOS
    val bubbleColor = when {
        isSos -> SosRed.copy(alpha = 0.18f)
        msg.isMine -> Color(0xFF0C4A6E)
        else -> ChatCard
    }
    val profile = if (msg.isMine) myProfile else (senderProfile ?: RiderProfile(name = msg.senderName))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!msg.isMine) {
            RiderAvatar(profile, size = 32)
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isMine) 16.dp else 4.dp,
                bottomEnd = if (msg.isMine) 4.dp else 16.dp
            ),
            color = bubbleColor,
            border = if (isSos) BorderStroke(1.5.dp, SosRed) else null,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!msg.isMine) {
                    Text(msg.senderName, color = riderColor(profile.colorIndex), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                when (msg.type) {
                    MeshType.SOS -> {
                        Text("🆘 SOS — needs help!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    MeshType.LOCATION -> Text("📍 Shared location", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    else -> Text(msg.text, color = Color.White, fontSize = 15.sp)
                }
                val lat = msg.latitude
                val lon = msg.longitude
                if (lat != null && lon != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpenMap(lat, lon) },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.25f)
                    ) {
                        Text(
                            "%.5f, %.5f · Open in Maps".format(lat, lon),
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = ChatBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (isSos) {
                    Text("Location unavailable", color = ChatMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(time)
                        if (msg.isMine) {
                            append(" · ")
                            append(
                                when {
                                    msg.seenBy.isNotEmpty() -> "✓✓ Seen by ${msg.seenBy.size}"
                                    msg.state == DeliveryState.QUEUED -> "🕓 Waiting for riders"
                                    else -> "✓ Sent"
                                }
                            )
                        } else {
                            append(
                                when (msg.via) {
                                    Via.INTERNET -> " · via internet"
                                    else -> if (msg.hops <= 1) " · direct" else " · ${msg.hops} hops"
                                }
                            )
                        }
                    },
                    color = ChatMuted, fontSize = 10.5.sp
                )
            }
        }
    }
}
