package com.bikeride.intercom.feature.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.feature.ui.theme.AstraThemePresets
import com.bikeride.intercom.feature.ui.theme.LocalAstraTheme

/**
 * Appearance section for Theme Customization.
 * Presets:
 * 🔥 Astra Orange (default)
 * 🔵 Blue
 * 🟢 Green
 * 🟣 Purple
 * 🔴 Red
 * 🟡 Yellow
 * ⚪ Custom (with interactive HSV hue slider)
 */
@Composable
fun AppearanceSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeState = LocalAstraTheme.current
    var showCustomPicker by remember { mutableStateOf(themeState.presetId == "custom") }
    var customHue by remember { mutableFloatStateOf(0f) }

    val hueColors = remember {
        listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Palette, contentDescription = null, tint = themeState.accentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("APPEARANCE — ACCENT THEME", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        // Preset chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AstraThemePresets.presets.forEach { preset ->
                val isSelected = themeState.presetId == preset.id
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showCustomPicker = false
                            themeState.applyPreset(preset, context)
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) preset.color.copy(alpha = 0.25f) else Color(0xFF1E293B),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) preset.color else Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${preset.emoji} ${preset.name}",
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Custom Option Chip
            val isCustom = themeState.presetId == "custom"
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showCustomPicker = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isCustom) themeState.accentColor.copy(alpha = 0.25f) else Color(0xFF1E293B),
                border = BorderStroke(if (isCustom) 2.dp else 1.dp, if (isCustom) themeState.accentColor else Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚪", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Custom",
                        color = if (isCustom) Color.White else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Custom Slider Picker
        if (showCustomPicker) {
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Custom Color Picker", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(themeState.accentColor)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Hue gradient bar preview
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Brush.horizontalGradient(hueColors))
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = customHue,
                        onValueChange = { hue ->
                            customHue = hue
                            val hsv = floatArrayOf(hue, 0.95f, 1.0f)
                            val customColor = Color(android.graphics.Color.HSVToColor(hsv))
                            themeState.applyCustomColor(customColor, context)
                        },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = themeState.accentColor,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
