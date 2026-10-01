package com.bikeride.intercom.feature.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.bluetooth.AudioRouteType
import kotlinx.coroutines.delay

/**
 * Full-screen OLED-black riding HUD, built for gloves on a handlebar mount:
 * - Double-tap anywhere on the background to mute / unmute.
 * - Big one-tap convoy alerts (Slow down, Hi, Stop, Pit stop) that every rider hears aloud.
 * - Horn, Location (announced by voice) and audio route within thumb reach.
 */
@Composable
fun RidingHudOverlay(
    roomName: String,
    bikerCount: Int,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    audioRoute: AudioRouteType,
    onCycleRoute: () -> Unit,
    onTriggerHorn: () -> Unit,
    onEndRide: () -> Unit,
    latencyMs: Long,
    amplitude: Float,
    onExitHud: () -> Unit,
    isEmergencyAlert: Boolean = false,
    quickAlerts: List<String> = emptyList(),
    onQuickAlert: (String) -> Unit = {},
    onShareLocation: () -> Unit = {},
    voiceSosLabel: String = "Voice SOS: off",
    voiceSosOn: Boolean = false,
    onToggleVoiceSos: () -> Unit = {},
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var confirmation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            delay(1500)
            confirmation = null
        }
    }
    fun confirm(text: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        confirmation = text
    }
    val currentMuted by rememberUpdatedState(isMuted)

    val micColor by animateColorAsState(
        targetValue = if (isMuted) Color(0xFFD32F2F) else Color(0xFF00C853),
        label = "micColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Double-tap on any empty area toggles mute — no need to aim with gloves
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    onToggleMute()
                    confirm(if (currentMuted) "🎙️ Mic live" else "🔇 Muted")
                })
            }
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Status bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "🏍️ $roomName",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (bikerCount <= 1) "Waiting for riders…" else "🟢 $bikerCount riders · ${latencyMs}ms",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onExitHud,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Exit HUD", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("MINIMIZE", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }

            if (isEmergencyAlert) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFF1744), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "🚨 ALERT HORN · CONVOY SOS ACTIVE",
                        modifier = Modifier.padding(10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Hints + Voice SOS switch ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1E293B), modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Double-tap screen or wave to mute", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggleVoiceSos),
                    shape = RoundedCornerShape(10.dp),
                    color = if (voiceSosOn) Color(0xFF3F0D12) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (voiceSosOn) Color(0xFFFF5252) else Color(0xFF334155))
                ) {
                    Text(
                        voiceSosLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = if (voiceSosOn) Color(0xFFFF8A80) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            ChannelQuietCard(isActive = isActive, isMuted = isMuted, amplitude = amplitude)

            // ── Mute (centre) ──
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(micColor.copy(alpha = 0.25f))
                    .border(4.dp, micColor, CircleShape)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Mute",
                        tint = micColor,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(if (isMuted) "MUTED" else "MIC LIVE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }

            // ── Quick convoy alerts: 2 × 2 big buttons ──
            if (quickAlerts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickAlerts.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { label ->
                                QuickAlertButton(label, Modifier.weight(1f)) {
                                    onQuickAlert(label)
                                    confirm("✓ Sent: $label")
                                }
                            }
                        }
                    }
                }
            }

            // ── Horn · Location · Speaker ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val hornColor = if (isEmergencyAlert) Color(0xFFFF1744) else Color(0xFFFF9100)
                RoundAction(Icons.Filled.Campaign, "HORN", hornColor) {
                    onTriggerHorn()
                    confirm("🚨 Horn sent")
                }
                RoundAction(Icons.Filled.MyLocation, "LOCATION", Color(0xFF22C55E)) {
                    onShareLocation()
                    confirm("📍 Location shared")
                }
                RoundAction(
                    when (audioRoute) {
                        AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                        AudioRouteType.LOUDSPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                        AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                    },
                    when (audioRoute) {
                        AudioRouteType.HELMET_BLUETOOTH -> "HELMET"
                        AudioRouteType.LOUDSPEAKER -> "SPEAKER"
                        AudioRouteType.EARPIECE -> "EARPIECE"
                    },
                    Color(0xFF00B0FF),
                    onCycleRoute
                )
            }

            Button(
                onClick = onEndRide,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("END RIDE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            }
        }

        // Big confirmation so riders know a tap worked without reading small text
        AnimatedVisibility(
            visible = confirmation != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xE6111827), border = BorderStroke(2.dp, Color(0xFF38BDF8))) {
                Text(
                    confirmation ?: "",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun QuickAlertButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(62.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(2.dp, Color(0xFF475569))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .border(3.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(36.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
