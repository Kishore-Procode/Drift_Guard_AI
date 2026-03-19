package com.driftdetector.desktop.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Navy Blue Premium Palette
private val NavyPrimary = Color(0xFF1A237E)
private val NavyPrimaryLight = Color(0xFF3949AB)
private val AccentTeal = Color(0xFF00BFA5)
private val AccentOrange = Color(0xFFFF6D00)
private val AlertRed = Color(0xFFFF1744)
private val SuccessGreen = Color(0xFF00C853)

// Light Theme
private val LightColorScheme = lightColorScheme(
    primary = NavyPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAF6),
    onPrimaryContainer = NavyPrimary,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3E0),
    onTertiaryContainer = Color(0xFFE65100),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = AlertRed,
    onError = Color.White
)

// Dark Theme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7986CB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF283593),
    onPrimaryContainer = Color(0xFFC5CAE9),
    secondary = Color(0xFF64FFDA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFAB40),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFBF360C),
    onTertiaryContainer = Color(0xFFFFCCBC),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2D3F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFF6E6E),
    onError = Color.White
)

@Composable
fun DriftGuardTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
