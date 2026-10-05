package space.tejaswi.healthraw

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF006C67), onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1E8), onPrimaryContainer = Color(0xFF00201D),
    background = Color(0xFFF5FAF8), surface = Color(0xFFF5FAF8),
    onBackground = Color(0xFF18211F), onSurface = Color(0xFF18211F)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFF80D5C9), onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF005049), onPrimaryContainer = Color(0xFFB9F1E8),
    secondary = Color(0xFFB0CCC5), onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46), onSecondaryContainer = Color(0xFFCCE8E0),
    background = Color(0xFF101816), onBackground = Color(0xFFE0EAE5),
    surface = Color(0xFF101816), onSurface = Color(0xFFE0EAE5),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBFCAC4),
    outline = Color(0xFF89948E), outlineVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFF0B1210), surfaceContainerLow = Color(0xFF18201D),
    surfaceContainer = Color(0xFF1C2421), surfaceContainerHigh = Color(0xFF26302B),
    surfaceContainerHighest = Color(0xFF303A35)
)

@Composable fun HealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors, content = content)
}

data class JsonColors(val key: Color, val string: Color, val number: Color, val boolean: Color, val nil: Color)

@Composable fun jsonColors(): JsonColors = if (isSystemInDarkTheme()) {
    JsonColors(Color(0xFF9CCAFF), Color(0xFF80D5C9), Color(0xFFFFC785), Color(0xFFD8B4FF), Color(0xFFBFCAC4))
} else {
    JsonColors(Color(0xFF254E79), Color(0xFF006C67), Color(0xFF7E4800), Color(0xFF7743A5), Color(0xFF697078))
}
