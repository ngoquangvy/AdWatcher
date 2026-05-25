package com.adwatcher.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Premium deep space dark mode color system
val DeepDarkBg = Color(0xFF0C0C0E)        // #0c0c0e - pure cinematic dark
val CardDarkBg = Color(0xFF16161B)        // #16161b - elegant slate grey
val BorderDark = Color(0xFF262630)        // Subtle card border
val TextPrimary = Color(0xFFF1F1F3)       // Soft white
val TextSecondary = Color(0xFF9090A0)     // Muted lavender gray

val ElectricCyan = Color(0xFF00E5FF)      // Secondary active color
val NeonPurple = Color(0xFF7C4DFF)        // Accent/Primary brand color

// Custom Status indicator colors (No generic red/green)
val StatusHighRisk = Color(0xFFFF3D60)    // Curated high intensity Crimson red
val StatusMediumRisk = Color(0xFFFF9F0A)  // Vivid Amber orange
val StatusLowRisk = Color(0xFF30D158)     // Bright Mint green

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    secondary = ElectricCyan,
    background = DeepDarkBg,
    surface = CardDarkBg,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark
)

// Standard light color scheme as fallback
private val LightColorScheme = lightColorScheme(
    primary = NeonPurple,
    secondary = ElectricCyan,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    outline = Color(0xFFE0E0E0)
)

@Composable
fun AdWatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Force dark theme for that premium aesthetic!

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
