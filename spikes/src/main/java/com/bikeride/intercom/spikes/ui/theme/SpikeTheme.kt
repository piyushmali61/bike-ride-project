package com.bikeride.intercom.spikes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpikeDarkScheme = darkColorScheme(
    primary = Color(0xFF5CB8FF),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFF4FDBC4),
    onSecondary = Color(0xFF003730),
    tertiary = Color(0xFFFFB951),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFFC0C6CF),
    outline = Color(0xFF8A9199)
)

@Composable
fun SpikeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SpikeDarkScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = SpikeDarkScheme,
        content = content
    )
}
