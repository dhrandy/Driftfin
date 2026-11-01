package tv.jetflix.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import tv.jetflix.app.data.Avatars

/** A bundled profile avatar, drawn as a circle (the art is already circle-cropped; the clip is a safeguard). */
@Composable
fun AvatarImage(entry: Avatars.Entry, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(entry.res),
        contentDescription = entry.label,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape),
    )
}
