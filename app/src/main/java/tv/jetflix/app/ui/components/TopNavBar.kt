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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

enum class JetflixTab(val label: String) { HOME("Home"), SHOWS("Shows"), MOVIES("Movies"), MY_LIST("My Driftfin"), SEARCH("Search"), SETTINGS("Settings") }

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = "DRIFTFIN",
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
    /** Opaque bar for when content scrolls underneath, so tab labels never sit on top of posters. */
    solid: Boolean = false,
    onNavFocusChange: (Boolean) -> Unit = {},
    /** Backdrop of what is on screen; when the bar is solid it shows a blurred, darkened copy of it. */
    blurArtUrl: String? = null,
) {
    Box(
        modifier = modifier
            .onFocusChanged { onNavFocusChange(it.hasFocus) }
            .then(if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier)
            .fillMaxWidth(),
    ) {
        val canBlur = true
        if (!solid && blurArtUrl != null && canBlur) {
            // On Home the bar sits on a blurred, colour-matched copy of the featured title (drawn by the page itself).
        } else if (solid) {
            if (blurArtUrl != null && canBlur) {
                // A blurred copy of the current artwork with a dark scrim: readable, and it follows what you are on.
                Box(Modifier.matchParentSize().clipToBounds()) {
                    ArtImage(
                        url = blurSource(blurArtUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().blurIfSupported(28.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color(0xFF141414).copy(alpha = 0.62f)))
                }
            } else {
                // No artwork, or a device without blur support: an opaque bar, so labels never sit on posters.
                Box(Modifier.matchParentSize().background(JetflixColors.Background))
            }
        } else if (blurArtUrl == null || !canBlur) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0f)))))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
        Wordmark()
        Spacer(Modifier.weight(1f))
        // Search is a magnifier icon at the start of the menu, as on the current Netflix TV menu.
        SearchIconTab(
            selected = selected == JetflixTab.SEARCH,
            onClick = { onTabSelected(JetflixTab.SEARCH) },
            // On the Search page the magnifier is the selected tab, so Back from the page lands here.
            modifier = if (selected == JetflixTab.SEARCH && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
        )
        JetflixTab.entries.filter { it != JetflixTab.SEARCH }.forEach { tab ->
            NavTab(
                label = tab.label,
                selected = tab == selected,
                onClick = { onTabSelected(tab) },
                modifier = if (tab == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
        Spacer(Modifier.weight(1f))
        if (userName != null) {
            // Signing out loses the session, so the first OK only asks; a second OK within 5 seconds confirms.
            var confirming by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(confirming) {
                if (confirming) { kotlinx.coroutines.delay(5_000); confirming = false }
            }
            NavTab(
                label = if (confirming) "Sign out? Press OK again" else userName,
                selected = false,
                onClick = { if (confirming) { confirming = false; onSignOut() } else confirming = true },
                onFocusLost = { confirming = false },
            )
        }
        }
    }
}

@Composable
private fun SearchIconTab(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color.Black else JetflixColors.TextPrimary,
            focusedContainerColor = Color(0xFFD0D0D0),
            focusedContentColor = Color.Black,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(50)),
    ) {
        // Magnifier drawn by hand (the extended icon set is not a dependency).
        androidx.compose.foundation.Canvas(
            Modifier
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .size(18.dp)
                .semantics { contentDescription = "Search" },
        ) {
            val w = size.width * 0.12f
            drawCircle(color = Color.White, radius = size.width * 0.34f, center = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w))
            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.66f), end = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.95f), strokeWidth = w)
        }
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocusLost: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (focused && !it.isFocused) onFocusLost(); focused = it.isFocused },
        colors = ClickableSurfaceDefaults.colors(
            // Current tab: white pill, dark text. Focused non-current tab: light gray pill.
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = when {
                selected -> Color.Black
                else -> JetflixColors.TextSecondary
            },
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(50)),
    ) {
        Text(
            text = label,
            style = JetflixType.body,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}


/** Back from page content goes to the selected tab in the top menu first; Back from the menu leaves. */
class MenuBack {
    val focus = androidx.compose.ui.focus.FocusRequester()
    var navHasFocus by mutableStateOf(false)
}

@Composable
fun rememberMenuBack(): MenuBack {
    val menu = remember { MenuBack() }
    androidx.activity.compose.BackHandler(enabled = !menu.navHasFocus) {
        try { menu.focus.requestFocus() } catch (_: Exception) { }
    }
    return menu
}

/**
 * Fades content out as it scrolls up under the menu bar, so text and artwork end cleanly instead of being
 * sliced off mid-line. Only active while the list is scrolled; at the top the page may sit under the bar by design.
 */
fun Modifier.fadeUnderMenuBar(active: Boolean, barHeight: androidx.compose.ui.unit.Dp = 76.dp, fade: androidx.compose.ui.unit.Dp = 40.dp): Modifier =
    if (!active) this else this
        // Paints the page background over the strip under the bar, fading out below it. The list sits on that same
        // background, so this looks like fading the content out, without the full-screen offscreen layer a mask needs.
        .drawWithContent {
            drawContent()
            val bar = barHeight.toPx(); val f = fade.toPx()
            drawRect(
                Brush.verticalGradient(
                    colorStops = arrayOf(0f to JetflixColors.Background, (bar / (bar + f)) to JetflixColors.Background, 1f to Color.Transparent),
                    startY = 0f, endY = bar + f,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, minOf(size.height, bar + f)),
            )
        }
