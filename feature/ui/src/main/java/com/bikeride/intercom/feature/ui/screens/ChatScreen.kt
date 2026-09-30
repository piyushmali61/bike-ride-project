package com.bikeride.intercom.feature.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.bikeride.intercom.feature.ui.ChatViewModel
import com.bikeride.intercom.feature.ui.IntercomViewModel
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
 * Convoy Mesh Chat, laid out for riding: everything you press lives in a fixed panel at the
 * bottom, within thumb reach, with big targets for gloves. Messages hop phone-to-phone over
 * Bluetooth with no internet, and also travel over public relays whenever any rider has data.
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
    val readAloud by viewModel.announceEnabled.collectAsState()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    var confirmSos by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var sendingPhoto by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            sendingPhoto = true
            viewModel.sendPhoto(uri) { ok ->
                sendingPhoto = false
                if (!ok) Toast.makeText(context, "Couldn't send that photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Mesh Chat · $room", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bluetooth, null, tint = if (btLinks > 0) ChatBlue else ChatDim, modifier = Modifier.size(13.dp))
                            Text(
                                when {
                                    !btRunning -> " Bluetooth off"
                                    else -> " $btLinks nearby"
                                },
                                color = ChatMuted, fontSize = 12.sp,
                                modifier = if (!btRunning) Modifier.clickable { viewModel.retryBluetooth() } else Modifier
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(if (relays > 0) Icons.Filled.Cloud else Icons.Filled.CloudOff, null, tint = if (relays > 0) ChatGreen else ChatDim, modifier = Modifier.size(13.dp))
                            Text(if (relays > 0) " online" else " offline", color = ChatMuted, fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("👥 ${activePeers.size}", color = ChatMuted, fontSize = 12.sp)
                        }
                    }
                    // Read-aloud toggle: incoming messages are spoken while riding
                    IconButton(onClick = { viewModel.setAnnounce(!readAloud) }) {
                        Icon(
                            if (readAloud) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Read messages aloud",
                            tint = if (readAloud) ChatGreen else ChatDim
                        )
                    }
                }
            }
        },
        bottomBar = {
            RidePanel(
                input = input,
                onInput = { input = it.take(ConvoyMesh.MAX_CHAT_CHARS) },
                onSend = {
                    viewModel.send(input)
                    input = ""
                },
                onSos = { confirmSos = true },
                onLocation = {
                    if (!viewModel.shareLocation()) {
                        Toast.makeText(context, "No location yet — turn on GPS", Toast.LENGTH_SHORT).show()
                    }
                },
                onQuickAlert = { viewModel.sendQuickAlert(it) },
                onPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                sendingPhoto = sendingPhoto
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (activePeers.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            }

            if (messages.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📡", fontSize = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No messages yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap a button below — it hops rider-to-rider over Bluetooth, even with no signal, " +
                            "and every rider hears it read aloud.",
                        color = ChatMuted, fontSize = 14.sp
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
                            onOpenImage = { viewingImage = it },
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
                    "Every rider in $room hears an alarm and your location, relayed through other phones if needed.",
                    color = ChatMuted, fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendSos(); confirmSos = false },
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed)
                ) { Text("SEND SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("CANCEL", color = ChatMuted) } }
        )
    }

    viewingImage?.let { path ->
        Dialog(onDismissRequest = { viewingImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { viewingImage = null },
                contentAlignment = Alignment.Center
            ) {
                PhotoFromFile(path, Modifier.fillMaxWidth(), ContentScale.Fit)
            }
        }
    }
}

/** Fixed bottom panel: the only place riders need to reach, sized for gloves. */
@Composable
private fun RidePanel(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onSos: () -> Unit,
    onLocation: () -> Unit,
    onQuickAlert: (String) -> Unit,
    onPhoto: () -> Unit,
    sendingPhoto: Boolean
) {
    Surface(color = ChatBar) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("🆘  SOS", SosRed, Modifier.weight(1f).height(60.dp), filled = true, onClick = onSos)
                BigButton("📍  LOCATION", ChatGreen, Modifier.weight(1f).height(60.dp), filled = false, onClick = onLocation)
            }
            IntercomViewModel.QUICK_ALERTS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { label ->
                        BigButton(label, Color(0xFF475569), Modifier.weight(1f).height(52.dp), filled = false) { onQuickAlert(label) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(ChatInner).clickable(enabled = !sendingPhoto, onClick = onPhoto),
                    contentAlignment = Alignment.Center
                ) {
                    if (sendingPhoto) CircularProgressIndicator(Modifier.size(22.dp), color = ChatBlue, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.PhotoCamera, contentDescription = "Send photo", tint = ChatBlue)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = { Text("Message…", color = ChatDim) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
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
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (input.isBlank()) ChatInner else Color(0xFF0EA5E9))
                        .clickable(enabled = input.isNotBlank(), onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, color: Color, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (filled) color else color.copy(alpha = 0.14f),
        border = BorderStroke(2.dp, color)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun PeerBadge(profile: RiderProfile, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        RiderAvatar(profile, size = 38)
        Text(profile.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, color = ChatDim, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun PhotoFromFile(path: String, modifier: Modifier, scale: ContentScale) {
    val bitmap = remember(path) {
        try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Photo", modifier = modifier, contentScale = scale)
    } else {
        Text("Photo unavailable", color = ChatMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    senderProfile: RiderProfile?,
    myProfile: RiderProfile,
    onOpenImage: (String) -> Unit,
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
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!msg.isMine) {
                    Text(msg.senderName, color = riderColor(profile.colorIndex), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                when (msg.type) {
                    MeshType.SOS -> Text("🆘 SOS — needs help!", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    MeshType.LOCATION -> Text("📍 Shared location", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    MeshType.IMAGE -> {
                        val path = msg.imagePath
                        if (path != null) {
                            PhotoFromFile(
                                path,
                                Modifier.padding(top = 4.dp).widthIn(max = 260.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpenImage(path) },
                                ContentScale.Crop
                            )
                        } else {
                            Text("📷 Receiving photo ${msg.imageReceived}/${msg.imageTotal}", color = Color.White, fontSize = 15.sp)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (msg.imageTotal > 0) msg.imageReceived / msg.imageTotal.toFloat() else 0f },
                                modifier = Modifier.width(180.dp),
                                color = ChatBlue,
                                trackColor = ChatInner
                            )
                        }
                    }
                    else -> Text(msg.text, color = Color.White, fontSize = 17.sp)
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
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            color = ChatBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
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
                    color = ChatMuted, fontSize = 11.sp
                )
            }
        }
    }
}
