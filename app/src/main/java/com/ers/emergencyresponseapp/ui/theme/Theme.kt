package com.ers.emergencyresponseapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4C8A89),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3A506B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1C2541),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF16181D),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4C8A89),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3A506B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1C2541),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFFAFAFA),
    onSurfaceVariant = Color(0xFF575757),
    outline = Color(0xFFE5E5E5)
)
@Composable
fun EmergencyResponseAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
