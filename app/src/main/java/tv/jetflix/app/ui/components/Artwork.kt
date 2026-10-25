package tv.jetflix.app.ui.components

import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * Test hook: when set (Paparazzi snapshots), artwork resolves synchronously from
 * this map instead of Coil. Keys are the exact image URLs the app requests.
 * Production leaves this null and loads over the network.
 */
val LocalTestArtwork = staticCompositionLocalOf<((String?) -> Painter?)?> { null }

@Composable
fun ArtImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackColor: Color = Color(0xFF2F2F2F),
) {
    val testProvider = LocalTestArtwork.current
    if (testProvider != null) {
        val painter = url?.let { testProvider(it) }
        if (painter != null) {
            Image(painter = painter, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
        } else {
            Box(modifier.background(fallbackColor))
        }
        return
    }
    androidx.compose.runtime.LaunchedEffect(url) { PerfCounters.inc("imgLoads") }
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(150)
            .build(),
    )
    Image(painter = painter, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

/**
 * Artwork for a blurred backdrop, requested small from the server. Android 12+ also blurs live with a render effect; older
 * devices scale a 48 px copy up with smoothing, which looks blurred on any API level.
 */
fun blurSource(url: String): String {
    // A blurred image never needs full resolution: decode 320 px (live blur, API 31+) or 48 px (older, scaled up) instead of 1280-1920.
    val w = if (liveBlurActive()) 320 else 48
    return if (url.contains("maxWidth=")) url.replace(Regex("maxWidth=\\d+"), "maxWidth=$w")
    else url + (if (url.contains("?")) "&" else "?") + "maxWidth=$w"
}

/** Live blur only where the platform supports it; the pre-shrunk source covers the rest. */
fun androidx.compose.ui.Modifier.blurIfSupported(radius: androidx.compose.ui.unit.Dp): androidx.compose.ui.Modifier =
    if (liveBlurActive()) this.blur(radius) else this

private fun liveBlurActive() = android.os.Build.VERSION.SDK_INT >= 31 && tv.jetflix.app.data.AppSettings.liveBlur.value
