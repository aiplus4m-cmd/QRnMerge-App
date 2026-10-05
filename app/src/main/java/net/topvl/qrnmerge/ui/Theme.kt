package net.topvl.qrnmerge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colours taken from the NhảmStudio logo.
val BrandBlue = Color(0xFF2473BA)
val BrandOrange = Color(0xFFF28A2C)

private val Light = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF0B3256),
    secondary = BrandOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CC),
    onSecondaryContainer = Color(0xFF5A2C00),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF1F7),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF5),
    outlineVariant = Color(0xFFD7DEE8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DC0F0),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF1B5A93),
    onPrimaryContainer = Color(0xFFD6E6F7),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4A2400),
    secondaryContainer = Color(0xFF7A3E00),
    onSecondaryContainer = Color(0xFFFFE3CC),
    background = Color(0xFF111418),
    surface = Color(0xFF16191E),
    surfaceVariant = Color(0xFF262B33),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF242930),
)

@Composable
fun QRnMergeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content,
    )
}
