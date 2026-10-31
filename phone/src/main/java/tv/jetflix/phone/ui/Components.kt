package tv.jetflix.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.BaseItemDto

@Composable
fun Art(url: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    if (url == null) Box(modifier.background(DriftfinColors.SurfaceLight))
    else AsyncImage(model = url, contentDescription = null, imageLoader = Graph.images, modifier = modifier, contentScale = scale)
}


/** Initials for an actor with no photo: first letter of the first word plus first letter of the last
 *  word, uppercased; a single-word name gets one letter. Non-ASCII letters are kept as-is. */
internal fun actorInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    val first = words.first().first()
    val last = words.last().first()
    return (if (words.size == 1) "$first" else "$first$last").uppercase()
}

/** An actor photo, or - when the person has no Primary image - a dark gradient disc with their
 *  initials (no network fetch, no ring or shadow). Sizes are tuned for the 72.dp cast-row disc the
 *  modifier supplies: the initials sit near 60% of the disc's height. */
@Composable
fun ActorAvatar(url: String?, name: String, modifier: Modifier = Modifier) {
    if (url != null) { Art(url, modifier); return }
    val initials = actorInitials(name)
    Box(
        modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF4A5468), androidx.compose.ui.graphics.Color(0xFF1E2129)))),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotEmpty()) {
            Text(initials, color = androidx.compose.ui.graphics.Color(0xFFF0F0F0), fontWeight = FontWeight.Bold, fontSize = if (initials.length == 1) 43.sp else 32.sp)
        }
    }
}

/** A 2:3 cover with a watched check and a resume bar. 4K shows on movies and single episodes only, never a whole series. */
@Composable
fun PosterCard(item: BaseItemDto, modifier: Modifier = Modifier, titlePoster: Boolean = false, onClick: () -> Unit) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(DriftfinColors.Surface).clickable(onClick = onClick)) {
        Art(if (titlePoster) Graph.repo.titlePosterUrl(item, 400) else Graph.repo.posterUrl(item, 400), Modifier.matchParentSize())
        val data = item.UserData
        if (data?.Played == true) {
            Text("✓", Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(DriftfinColors.Red).padding(horizontal = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (tv.jetflix.phone.data.PhoneSettings.formatBadges.collectAsState().value && tv.jetflix.phone.data.Resolution4K.badge(item)) {
            Text("4K", Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(0xCC000000)).padding(horizontal = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        val pct = data?.PlayedPercentage
        if (pct != null && pct in 1.0..99.0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(DriftfinColors.ProgressTrack))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((pct / 100.0).toFloat()).height(4.dp).background(DriftfinColors.Red))
        }
    }
}

@Composable
fun SectionRow(title: String, items: List<BaseItemDto>, cardWidth: Dp = 112.dp, titlePoster: Boolean = false, onOpen: (BaseItemDto) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 20.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
            items(items, key = { it.Id }) { PosterCard(it, Modifier.width(cardWidth), titlePoster) { onOpen(it) } }
        }
    }
}

/** The title's own logo when the setting is on and the server has one; plain text while it loads or when there is none. The title stays available to screen readers. */
@Composable
fun TitleOrLogo(item: BaseItemDto, textSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, maxLogoHeight: Dp = 72.dp) {
    val enabled by tv.jetflix.phone.data.PhoneSettings.titleLogos.collectAsState()
    val title = item.displayTitle()
    val text: @Composable () -> Unit = { Text(title, fontSize = textSize, fontWeight = FontWeight.Bold, maxLines = 2, modifier = modifier) }
    if (!enabled) { text(); return }
    val url = Graph.repo.logoUrl(item)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val painter = coil.compose.rememberAsyncImagePainter(
        coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(url)
            .size(with(density) { 320.dp.roundToPx() }, with(density) { maxLogoHeight.roundToPx() }).build(),
        imageLoader = Graph.images,
    )
    when (painter.state) {
        is coil.compose.AsyncImagePainter.State.Success ->
            androidx.compose.foundation.Image(painter, null, modifier.height(maxLogoHeight).widthIn(max = 320.dp)
                .semantics { contentDescription = title }, alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
        else -> text()
    }
}

/** Funnel bars for the Sort control (the core icon set has no sort glyph). Same 24dp style as the other detail icons. */
val SortIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Sort", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes("M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"),
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}
