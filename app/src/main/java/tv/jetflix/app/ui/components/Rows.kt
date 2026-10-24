package tv.jetflix.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Text
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/** One titled horizontal row of media cards. */
@Composable
fun MediaRow(
    title: String,
    items: List<BaseItemDto>,
    wide: Boolean,
    imageUrlFor: (BaseItemDto) -> String?,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    focusMemory: FocusMemory? = null,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = JetflixType.sectionTitle,
            color = JetflixColors.TextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.Id }) { item ->
                if (wide) {
                    WideCard(item = item, imageUrl = imageUrlFor(item), modifier = if (focusMemory != null) Modifier.restoreFocus(focusMemory, "$title|${item.Id}") else Modifier, onClick = { onItemClick(item) })
                } else {
                    PosterCard(item = item, imageUrl = imageUrlFor(item), modifier = if (focusMemory != null) Modifier.restoreFocus(focusMemory, "$title|${item.Id}") else Modifier, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
