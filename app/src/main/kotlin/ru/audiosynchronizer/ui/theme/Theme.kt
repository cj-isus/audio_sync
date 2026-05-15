package ru.audiosynchronizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF2196F3),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE3F2FD),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF0D47A1),
    secondary = androidx.compose.ui.graphics.Color(0xFF4CAF50),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8F5E8),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1B5E20),
    tertiary = androidx.compose.ui.graphics.Color(0xFF9C27B0),
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFF3E5F5),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF4A148C),
    error = androidx.compose.ui.graphics.Color(0xFFB00020),
    onError = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1C1C),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1C1C),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF666666),
    outline = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF90CAF9),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0D47A1),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF1565C0),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE3F2FD),
    secondary = androidx.compose.ui.graphics.Color(0xFFA5D6A7),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF1B5E20),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF2E7D32),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8F5E8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFCE93D8),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF4A148C),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF7B1FA2),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFF3E5F5),
    error = androidx.compose.ui.graphics.Color(0xFFCF6679),
    onError = androidx.compose.ui.graphics.Color(0xFF690005),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0B0B0),
    outline = androidx.compose.ui.graphics.Color(0xFF555555),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
)

@Composable
fun AudioSynchronizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
