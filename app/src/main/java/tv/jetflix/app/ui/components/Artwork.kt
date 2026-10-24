package tv.jetflix.app.ui.components

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
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(150)
            .build(),
    )
    Image(painter = painter, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}
