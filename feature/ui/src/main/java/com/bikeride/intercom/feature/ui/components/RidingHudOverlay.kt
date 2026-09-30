package com.bikeride.intercom.feature.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.bikeride.intercom.bluetooth.AudioRouteType

/**
 * Ultra-high-contrast OLED black full-screen HUD mode for motorcyclists.
 * Features oversized touch targets (≥ 120dp) specifically engineered
 * for handlebar-mounted phones and thick motorcycle riding gloves.
 * Displays real-time Room status and Voice Mute indicator ("Say Mute to Mute").
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
    modifier: Modifier = Modifier
) {
    val micButtonColor by animateColorAsState(
        targetValue = if (isMuted) Color(0xFFD32F2F) else Color(0xFF00C853),
        label = "micColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🏍️ ROOM: $roomName",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "🟢 $bikerCount Biker${if (bikerCount > 1) "s" else ""} Connected · ${latencyMs}ms",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = onExitHud,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Exit HUD", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("MINIMIZE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Voice Command Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Voice Command Active: Just say \"MUTE\" or \"UNMUTE\"",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Audio Waveform
            AudioWaveVisualizer(
                amplitude = amplitude,
                isMuted = isMuted,
                barCount = 13,
                maxHeight = 70.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Primary Glove-Friendly Controls (Centerpiece)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Giant 150dp MUTE button
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(micButtonColor.copy(alpha = 0.25f))
                        .border(4.dp, micButtonColor, CircleShape)
                        .clickable(onClick = onToggleMute),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mute",
                            tint = micButtonColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isMuted) "MUTED" else "MIC LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap or say 'Mute'",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Bottom Dual Control Row: HORN + AUDIO ROUTE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Giant 100dp HORN Alert Button
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9100).copy(alpha = 0.25f))
                            .border(3.dp, Color(0xFFFF9100), CircleShape)
                            .clickable(onClick = onTriggerHorn),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Campaign,
                                contentDescription = "Horn",
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                "HORN",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Giant 100dp Audio Output Route Button
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00B0FF).copy(alpha = 0.25f))
                            .border(3.dp, Color(0xFF00B0FF), CircleShape)
                            .clickable(onClick = onCycleRoute),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = when (audioRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> Icons.Filled.Headset
                                    AudioRouteType.LOUDSPEAKER -> Icons.Filled.VolumeUp
                                    AudioRouteType.EARPIECE -> Icons.Filled.PhoneInTalk
                                },
                                contentDescription = "Route",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = when (audioRoute) {
                                    AudioRouteType.HELMET_BLUETOOTH -> "HELMET"
                                    AudioRouteType.LOUDSPEAKER -> "SPEAKER"
                                    AudioRouteType.EARPIECE -> "EARPIECE"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Big Glove-Friendly End Ride button in HUD
            Button(
                onClick = onEndRide,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("END RIDE CONVOY", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
