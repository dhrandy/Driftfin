package tv.jetflix.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/**
 * The show's own title logo (Jellyfin "Logo" image) when it exists and the setting is on; plain text otherwise,
 * including while the logo loads and whenever the server has none. The title stays available to screen readers.
 */
internal fun logoRequest(context: android.content.Context, logoUrl: String, widthPx: Int, heightPx: Int): ImageRequest =
    // Without an explicit size Coil 2.7 waits for the painter's first draw (AsyncImagePainter.onDraw sets drawSize),
    // but the Image is only composed on Success - so the request would never start and the logo would never show.
    ImageRequest.Builder(context).data(logoUrl).size(widthPx, heightPx).crossfade(150).build()
@Composable
fun TitleOrLogo(
    title: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    maxLogoHeight: Dp = 96.dp,
    maxLogoWidth: Dp = 420.dp,
    maxTextLines: Int = 2,
    textStyle: androidx.compose.ui.text.TextStyle = JetflixType.heroTitle,
    textColor: androidx.compose.ui.graphics.Color = JetflixColors.TextPrimary,
) {
    val enabled by AppSettings.titleLogos.collectAsState()
    val text: @Composable () -> Unit = {
        androidx.tv.material3.Text(
            text = title, style = textStyle, color = textColor,
            maxLines = maxTextLines, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
    }
    if (!enabled || logoUrl == null) { text(); return }
    val testProvider = LocalTestArtwork.current
    val logoModifier = modifier.height(maxLogoHeight).widthIn(max = maxLogoWidth).semantics { contentDescription = title }
    if (testProvider != null) {
        val p = testProvider(logoUrl)
        if (p == null) text() else Image(p, null, logoModifier, alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val painter = rememberAsyncImagePainter(
        logoRequest(LocalContext.current, logoUrl, with(density) { maxLogoWidth.roundToPx() }, with(density) { maxLogoHeight.roundToPx() }),
    )
    when (painter.state) {
        is AsyncImagePainter.State.Success ->
            Image(painter, null, logoModifier, alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        else -> text()
    }
}
