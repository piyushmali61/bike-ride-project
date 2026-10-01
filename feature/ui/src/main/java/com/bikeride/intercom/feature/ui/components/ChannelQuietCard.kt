package com.bikeride.intercom.feature.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Voice activity card: "CHANNEL QUIET · No incoming audio" with idle dots, or a live wave while
 * someone speaks. Shared by the home screen and both Live HUD views.
 */
@Composable
fun ChannelQuietCard(
    isActive: Boolean,
    isMuted: Boolean,
    amplitude: Float
) {
    val isSpeaking = isActive && !isMuted && amplitude > 0.05f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D).copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, Color(0xFF1E2D42))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C2538)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isSpeaking) "VOICE ACTIVE" else "CHANNEL QUIET",
                        color = Color(0xFF00E5FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = if (isSpeaking) "Speaking to convoy..." else "No incoming audio",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }
            }

            if (isSpeaking) {
                AudioWaveVisualizer(
                    amplitude = amplitude,
                    isMuted = false,
                    barCount = 10,
                    maxHeight = 20.dp,
                    modifier = Modifier.width(120.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(10) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2DD4BF))
                        )
                    }
                }
            }
        }
    }
}

