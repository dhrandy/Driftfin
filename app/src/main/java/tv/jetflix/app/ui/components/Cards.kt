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
private fun cardScale() = CardDefaults.scale(scale = 1f, focusedScale = 1.08f)

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

/** 2:3 poster card used in rows and grids. */
@Composable
fun PosterCard(
    item: BaseItemDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
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
                ResumeProgressBar(item.UserData?.PlayedPercentage)
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
    width: Dp = 260.dp,
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
                    ResumeProgressBar(item.UserData?.PlayedPercentage)
                }
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
