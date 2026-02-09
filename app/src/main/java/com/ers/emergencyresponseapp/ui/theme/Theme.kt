package com.ers.emergencyresponseapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Primary1,
    secondary = Secondary1,
    tertiary = Tertiary1,
    background = Dark1,
    surface = CardBg1,
    // use light-on-dark readable text colors for dark scheme
    onBackground = Color.White,
    onSurface = Color.Black,
    onPrimary = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Primary1,
    secondary = Secondary1,
    tertiary = Tertiary1,
    background = BgColor1,
    surface = CardBg1,
    // use dark text for light scheme
    onBackground = TextPrimary1,
    onSurface = TextPrimary1,
    onPrimary = BgColor1
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
