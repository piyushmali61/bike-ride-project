package com.bikeride.intercom.feature.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real-time reactive audio visualizer that animates smoothly
 * according to the live microphone and peer voice amplitude.
 */
@Composable
fun AudioWaveVisualizer(
    amplitude: Float,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    maxHeight: Dp = 64.dp,
    activeColor: Color = Color(0xFF00E676), // Neon Green
    secondaryColor: Color = Color(0xFF00B0FF), // Neon Cyan
    mutedColor: Color = Color(0xFFFF5252) // Warning Red
) {
    val barAmplitudes = remember(barCount) {
        // Multipliers for symmetric visualizer wave shape
        val list = mutableListOf<Float>()
        val mid = barCount / 2f
        for (i in 0 until barCount) {
            val dist = kotlin.math.abs(i - mid) / mid
            list.add(1f - (dist * 0.45f))
        }
        list
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val targetAmp = if (isMuted) 0.05f else maxOf(0.08f, amplitude)

        val animatedAmp by animateFloatAsState(
            targetValue = targetAmp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "visualizerAmp"
        )

        for (i in 0 until barCount) {
            val multiplier = barAmplitudes[i]
            val barHeightFraction = (animatedAmp * multiplier).coerceIn(0.12f, 1.0f)
            val barHeight = maxHeight * barHeightFraction

            val brush = if (isMuted) {
                Brush.verticalGradient(listOf(mutedColor.copy(alpha = 0.5f), mutedColor))
            } else {
                Brush.verticalGradient(listOf(secondaryColor, activeColor))
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(6.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(brush)
            )
        }
    }
}
