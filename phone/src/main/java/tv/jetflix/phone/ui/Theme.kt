package tv.jetflix.phone.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Same palette as the TV app: red brand accent on near-black. */
object DriftfinColors {
    val Red = Color(0xFFE50914)
    val Background = Color(0xFF141414)
    val Surface = Color(0xFF1F1F1F)
    val SurfaceLight = Color(0xFF2F2F2F)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF808080)
    val ProgressTrack = Color(0xFF4D4D4D)
}

private val scheme = darkColorScheme(
    primary = DriftfinColors.Red,
    background = DriftfinColors.Background,
    surface = DriftfinColors.Surface,
    onPrimary = Color.White,
    onBackground = DriftfinColors.TextPrimary,
    onSurface = DriftfinColors.TextPrimary,
    error = Color(0xFFFF6E40),
)

/** The app is dark only, like the TV app. */
@Composable
fun DriftfinTheme(content: @Composable () -> Unit) {
    // The Surface sets the default text color to white. Without it, Text with no explicit color draws black on the dark background.
    MaterialTheme(colorScheme = scheme) {
        androidx.compose.material3.Surface(color = DriftfinColors.Background, contentColor = DriftfinColors.TextPrimary, content = content)
    }
}
