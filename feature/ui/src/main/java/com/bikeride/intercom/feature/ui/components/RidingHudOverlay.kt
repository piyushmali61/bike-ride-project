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
 * Ultra-high-contrast OLED black full-screen HUD mode.
 * Features oversized touch targets (≥ 120dp) specifically engineered
 * for handlebar-mounted phones and thick motorcycle riding gloves.
 */
@Composable
fun RidingHudOverlay(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    audioRoute: AudioRouteType,
    onCycleRoute: () -> Unit,
    onTriggerHorn: () -> Unit,
    peerName: String?,
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
                        text = "🟢 RIDE ACTIVE · ${latencyMs}ms",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Connected: ${peerName ?: "Rider 2"}",
                        color = Color.White.copy(alpha = 0.7f),
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
                    Text("EXIT HUD", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Audio Waveform
            AudioWaveVisualizer(
                amplitude = amplitude,
                isMuted = isMuted,
                barCount = 13,
                maxHeight = 80.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Primary Glove-Friendly Controls (Centerpiece)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Giant 140dp MUTE button
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

            Spacer(Modifier.height(16.dp))
        }
    }
}
