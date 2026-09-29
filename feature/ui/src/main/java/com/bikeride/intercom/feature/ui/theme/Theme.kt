package com.bikeride.intercom.feature.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════
// Color palette — Dark theme default (Section 17: dark theme for night riding)
// ═══════════════════════════════════════════════════════════════════

// Primary: Electric Blue — represents connectivity
val PrimaryDark = Color(0xFF5CB8FF)
val OnPrimaryDark = Color(0xFF003258)
val PrimaryContainerDark = Color(0xFF00497D)
val OnPrimaryContainerDark = Color(0xFFD1E4FF)

// Secondary: Teal accent for local/proximity
val SecondaryDark = Color(0xFF4FDBC4)
val OnSecondaryDark = Color(0xFF003730)
val SecondaryContainerDark = Color(0xFF005047)
val OnSecondaryContainerDark = Color(0xFF72F8DF)

// Tertiary: Amber for warnings/attention
val TertiaryDark = Color(0xFFFFB951)
val OnTertiaryDark = Color(0xFF462A00)
val TertiaryContainerDark = Color(0xFF643F00)
val OnTertiaryContainerDark = Color(0xFFFFDDB3)

// Error
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

// Background & Surface
val BackgroundDark = Color(0xFF0F1419)
val OnBackgroundDark = Color(0xFFE1E2E8)
val SurfaceDark = Color(0xFF161B22)
val OnSurfaceDark = Color(0xFFE1E2E8)
val SurfaceVariantDark = Color(0xFF1E252E)
val OnSurfaceVariantDark = Color(0xFFC0C6CF)
val OutlineDark = Color(0xFF8A9199)

// Custom semantic colors for transport status
object IntercomColors {
    val LocalActive = Color(0xFF4FDBC4)      // Teal — local/direct
    val InternetActive = Color(0xFF5CB8FF)   // Blue — internet
    val Switching = Color(0xFFFFB951)         // Amber — transitioning
    val WeakLink = Color(0xFFFF9E44)         // Orange — degraded
    val Disconnected = Color(0xFFFF6B6B)     // Red — lost
    val Paused = Color(0xFF8A9199)           // Gray — paused
    val Speaking = Color(0xFF4FDBC4)         // Pulse animation color
    val MuteRed = Color(0xFFFF6B6B)          // Mute button
    val SafeGreen = Color(0xFF4ADE80)        // All-good indicator
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

// Light scheme (for accessibility preference)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0061A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF006B5E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF815600),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20)
)

@Composable
fun SmartIntercomTheme(
    darkTheme: Boolean = true, // Default dark for riding (Section 17)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Dynamic color from wallpaper
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = IntercomTypography,
        content = content
    )
}
