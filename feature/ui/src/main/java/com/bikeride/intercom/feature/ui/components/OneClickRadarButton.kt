package com.bikeride.intercom.feature.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.transport.local.nearby.MeshConnectionState

/**
 * The primary 1-Click action button with animated pulsing radar waves.
 * Tapping this initiates instant mesh discovery & auto-connection between phones.
 */
@Composable
fun OneClickRadarButton(
    connectionState: MeshConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = connectionState == MeshConnectionState.SEARCHING || connectionState == MeshConnectionState.CONNECTING
    val isConnected = connectionState == MeshConnectionState.CONNECTED

    // Infinite radar pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            isConnected -> Color(0xFF00C853) // Green
            isSearching -> Color(0xFFFF9100) // Amber / Seeking
            else -> Color(0xFF0284C7)        // Electric Blue
        },
        label = "coreColor"
    )

    Box(
        modifier = modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radar wave rings when searching or connecting
        if (isSearching) {
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(coreColor.copy(alpha = pulseAlpha))
            )
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale * 0.85f)
                    .clip(CircleShape)
                    .background(coreColor.copy(alpha = pulseAlpha * 0.7f))
            )
        }

        // Inner Core Button
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(170.dp)
                .shadow(
                    elevation = if (isConnected) 16.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = coreColor.copy(alpha = 0.5f),
                    spotColor = coreColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.9f),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .border(3.dp, coreColor, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Filled.Headset
                        isSearching -> Icons.Filled.Radar
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isConnected -> "CONNECTED"
                        isSearching -> "CONNECTING..."
                        else -> "TAP TO RIDE"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = when {
                        isConnected -> "Tap to Stop"
                        isSearching -> "Seeking Rider"
                        else -> "1-Click Intercom"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
