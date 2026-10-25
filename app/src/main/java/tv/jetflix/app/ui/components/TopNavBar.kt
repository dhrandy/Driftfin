package tv.jetflix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

enum class JetflixTab(val label: String) { HOME("Home"), SHOWS("Shows"), MOVIES("Movies"), MY_LIST("My List"), SEARCH("Search"), SETTINGS("Settings") }

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = "JETFLIX",
        style = JetflixType.wordmark,
        color = JetflixColors.Red,
        modifier = modifier,
    )
}

/**
 * Netflix-style top bar: wordmark left, tab labels center-left, user name right.
 * Tabs report focus+click so D-pad navigation works like Netflix's.
 */
@Composable
fun TopNavBar(
    selected: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    downFocus: androidx.compose.ui.focus.FocusRequester? = null,
    selectedFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0f)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Wordmark()
        Spacer(Modifier.width(10.dp))
        JetflixTab.entries.forEach { tab ->
            NavTab(
                label = tab.label,
                selected = tab == selected,
                onClick = { onTabSelected(tab) },
                modifier = if (tab == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
        Spacer(Modifier.weight(1f))
        if (userName != null) {
            NavTab(label = userName, selected = false, onClick = onSignOut)
        }
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = when {
                selected -> JetflixColors.TextPrimary
                else -> JetflixColors.TextSecondary
            },
            focusedContainerColor = JetflixColors.SurfaceLight,
            focusedContentColor = JetflixColors.TextPrimary,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = label,
            style = JetflixType.body,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
