package tv.jetflix.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object JetflixColors {
    val Red = Color(0xFFE50914)
    val Background = Color(0xFF141414)
    val Surface = Color(0xFF1F1F1F)
    val SurfaceLight = Color(0xFF2F2F2F)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val TextMuted = Color(0xFF808080)
    val FocusBorder = Color(0xFFFFFFFF)
    val ProgressTrack = Color(0xFF4D4D4D)
}

private val JetflixColorScheme = darkColorScheme(
    primary = JetflixColors.Red,
    onPrimary = Color.White,
    secondary = JetflixColors.SurfaceLight,
    onSecondary = Color.White,
    background = JetflixColors.Background,
    onBackground = JetflixColors.TextPrimary,
    surface = JetflixColors.Surface,
    onSurface = JetflixColors.TextPrimary,
    surfaceVariant = JetflixColors.SurfaceLight,
    onSurfaceVariant = JetflixColors.TextSecondary,
    error = Color(0xFFFF6E40),
    onError = Color.White,
    border = JetflixColors.SurfaceLight,
)

@Composable
fun JetflixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JetflixColorScheme,
        content = content,
    )
}

object JetflixType {
    val wordmark = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = 1.5.sp,
    )
    val heroTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 52.sp,
        lineHeight = 56.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
}
