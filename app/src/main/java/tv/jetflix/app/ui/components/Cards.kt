package tv.jetflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

private val cardShape = RoundedCornerShape(6.dp)

@Composable
private fun focusedBorder() = CardDefaults.border(
    border = Border(BorderStroke(0.dp, JetflixColors.SurfaceLight), shape = cardShape),
    focusedBorder = Border(BorderStroke(3.dp, JetflixColors.FocusBorder), shape = cardShape),
)

@Composable
private fun cardScale() = CardDefaults.scale(scale = 1f, focusedScale = 1.12f)

/**
 * Resume progress only for titles with a real saved play position. Jellyfin can send a percentage for items nobody
 * has started, so a bar needs a position above zero and the item must not be marked watched.
 */
fun BaseItemDto.resumePercent(): Double? {
    val d = UserData ?: return null
    if (d.Played || d.PlaybackPositionTicks <= 0L) return null
    return d.PlayedPercentage ?: RunTimeTicks?.takeIf { it > 0 }?.let { d.PlaybackPositionTicks * 100.0 / it }
}

/** Progress bar at the bottom of resumable cards, like Netflix's red bar. */
@Composable
fun ResumeProgressBar(percent: Double?, modifier: Modifier = Modifier) {
    if (percent == null || percent <= 0.0 || percent >= 100.0) return
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(JetflixColors.ProgressTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                .height(3.dp)
                .background(JetflixColors.Red),
        )
    }
}

/** White circle with a check, top-right of a card, for anything Jellyfin marks as fully watched. */
@Composable
fun WatchedBadge(item: BaseItemDto, modifier: Modifier = Modifier) {
    if (item.UserData?.Played != true) return
    androidx.compose.foundation.Canvas(
        modifier
            .padding(6.dp)
            .size(22.dp)
            .androidx_semantics("Watched"),
    ) {
        drawCircle(color = androidx.compose.ui.graphics.Color.White)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.69f)
            lineTo(size.width * 0.74f, size.height * 0.33f)
        }
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFF141414), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private fun Modifier.androidx_semantics(label: String): Modifier =
    this.then(androidx.compose.ui.Modifier.semantics { contentDescription = label })

/** 2:3 poster card used in rows and grids. */
@Composable
fun PosterCard(
    item: BaseItemDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp * tv.jetflix.app.data.AppSettings.posterSize.collectAsState().value.scale,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = CardDefaults.shape(shape = cardShape),
        scale = cardScale(),
        border = focusedBorder(),
    ) {
        Box {
            ArtImage(
                url = imageUrl,
                contentDescription = item.displayTitle(),
                modifier = Modifier
                    .width(width)
                    .aspectRatio(2f / 3f),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ResumeProgressBar(item.resumePercent())
            }
            if (item.Type == "BoxSet") {
                // Collections show their own name and how many titles they hold.
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(item.Name ?: "Collection", style = JetflixType.caption, color = JetflixColors.TextPrimary, maxLines = 2)
                    item.ChildCount?.let { Text(if (it == 1) "1 title" else "$it titles", style = JetflixType.caption, color = JetflixColors.TextSecondary) }
                }
            } else {
                WatchedBadge(item, Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** 16:9 wide card for Continue Watching / Next Up / episodes, with title text below. */
@Composable
fun WideCard(
    item: BaseItemDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp * tv.jetflix.app.data.AppSettings.posterSize.collectAsState().value.scale,
    showTitle: Boolean = true,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = CardDefaults.shape(shape = cardShape),
        scale = cardScale(),
        border = focusedBorder(),
    ) {
        Column {
            Box {
                ArtImage(
                    url = imageUrl,
                    contentDescription = item.displayTitle(),
                    modifier = Modifier
                        .width(width)
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    ResumeProgressBar(item.resumePercent())
                }
                WatchedBadge(item, Modifier.align(Alignment.TopEnd))
            }
            if (showTitle) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        text = item.episodeLabel()?.let { "$it · ${item.Name ?: ""}" } ?: item.displayTitle(),
                        style = JetflixType.caption,
                        color = JetflixColors.TextPrimary,
                        maxLines = 1,
                    )
                    if (item.isEpisode) {
                        Text(
                            text = item.SeriesName ?: "",
                            style = JetflixType.caption,
                            color = JetflixColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
