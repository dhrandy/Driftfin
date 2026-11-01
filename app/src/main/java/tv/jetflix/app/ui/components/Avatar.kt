package tv.jetflix.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.jetflix.app.data.Avatars
import tv.jetflix.app.ui.theme.JetflixColors

/**
 * A bundled profile avatar, drawn as a circle (the art is already circle-cropped; the clip is a
 * safeguard). Always carries a thin neutral ring so dark art edges stay readable on the dark
 * header; the ring turns red while the avatar holds remote focus (user-picked treatment, 10-31).
 */
@Composable
fun AvatarImage(entry: Avatars.Entry, size: Dp, modifier: Modifier = Modifier, focused: Boolean = false) {
    Image(
        painter = painterResource(entry.res),
        contentDescription = entry.label,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .border(
                androidx.compose.foundation.BorderStroke(
                    if (focused) 2.5.dp else 1.5.dp,
                    if (focused) JetflixColors.Red else JetflixColors.TextSecondary,
                ),
                CircleShape,
            )
            .clip(CircleShape),
    )
}
