package com.bikeride.intercom.feature.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class ThemeColorPreset(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Color
)

object AstraThemePresets {
    val AstraOrange = ThemeColorPreset("orange", "Astra Orange", "🔥", Color(0xFFFF6D00))
    val Blue = ThemeColorPreset("blue", "Blue", "🔵", Color(0xFF0EA5E9))
    val Green = ThemeColorPreset("green", "Green", "🟢", Color(0xFF22C55E))
    val Purple = ThemeColorPreset("purple", "Purple", "🟣", Color(0xFFA855F7))
    val Red = ThemeColorPreset("red", "Red", "🔴", Color(0xFFEF4444))
    val Yellow = ThemeColorPreset("yellow", "Yellow", "🟡", Color(0xFFEAB308))

    val presets = listOf(AstraOrange, Blue, Green, Purple, Red, Yellow)
    val defaultPreset = AstraOrange
}

class AstraThemeState(
    initialColor: Color = AstraThemePresets.defaultPreset.color,
    initialPresetId: String = AstraThemePresets.defaultPreset.id
) {
    var accentColor by mutableStateOf(initialColor)
    var presetId by mutableStateOf(initialPresetId)

    val glowColor: Color get() = accentColor.copy(alpha = 0.32f)
    val containerColor: Color get() = accentColor.copy(alpha = 0.16f)
    val subtleBorder: Color get() = accentColor.copy(alpha = 0.40f)

    fun applyPreset(preset: ThemeColorPreset, context: Context? = null) {
        accentColor = preset.color
        presetId = preset.id
        context?.let { saveToPrefs(it, preset.color, preset.id) }
    }

    fun applyCustomColor(color: Color, context: Context? = null) {
        accentColor = color
        presetId = "custom"
        context?.let { saveToPrefs(it, color, "custom") }
    }

    private fun saveToPrefs(context: Context, color: Color, preset: String) {
        val prefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("THEME_ACCENT_COLOR", color.toArgb())
            .putString("THEME_PRESET_ID", preset)
            .apply()
    }

    companion object {
        fun load(context: Context): AstraThemeState {
            val prefs = context.getSharedPreferences("astra_ride_prefs", Context.MODE_PRIVATE)
            val savedPreset = prefs.getString("THEME_PRESET_ID", AstraThemePresets.defaultPreset.id) ?: AstraThemePresets.defaultPreset.id
            val defaultArgb = AstraThemePresets.defaultPreset.color.toArgb()
            val savedArgb = prefs.getInt("THEME_ACCENT_COLOR", defaultArgb)
            return AstraThemeState(
                initialColor = Color(savedArgb),
                initialPresetId = savedPreset
            )
        }
    }
}

val LocalAstraTheme = compositionLocalOf { AstraThemeState() }
val LocalAstraAccent = compositionLocalOf { AstraThemePresets.defaultPreset.color }
