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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

enum class JetflixTab(val label: String) { HOME("Home"), SHOWS("Shows"), MOVIES("Movies"), MY_LIST("My Driftfin"), SEARCH("Search"), SETTINGS("Settings") }

/**
 * True while the user is moving across the top tabs. Each page owns its own top bar, so opening a page by focus
 * has to hand focus to the new page's bar, and the new page must not pull focus down into its content until Down.
 */
object NavSweep {
    @Volatile var active: Boolean = false
    @Volatile var tab: JetflixTab? = null
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    // The approved squared DRIFTFIN lettering, same footprint as the old text (about 143 x 18 dp).
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(tv.jetflix.app.R.drawable.wordmark_driftfin),
        contentDescription = "Driftfin",
        modifier = modifier.height(18.dp),
    )
}

/**
 * TV-style top bar: wordmark left, tab labels center-left, user name right.
 * Tabs report focus+click so D-pad navigation works with the remote.
 */
@Composable
fun TopNavBar(
    selected: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    /** OK on the user name opens the profile picker. */
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier,
    downFocus: androidx.compose.ui.focus.FocusRequester? = null,
    selectedFocus: androidx.compose.ui.focus.FocusRequester? = null,
    /** Opaque bar for when content scrolls underneath, so tab labels never sit on top of posters. */
    solid: Boolean = false,
    onNavFocusChange: (Boolean) -> Unit = {},
    /** Backdrop of what is on screen; when the bar is solid it shows a blurred, darkened copy of it. */
    blurArtUrl: String? = null,
) {
    CountRecompose("navBar")
    // A focused tab opens its page after a short dwell, including the first tab focused on entering the bar.
    var barHasFocus by remember { mutableStateOf(false) }
    var barHadFocus by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(barHasFocus) {
        if (barHasFocus) barHadFocus = true
        else if (barHadFocus) {
            // Focus passes through "nobody" for an instant when it moves between tabs; only a real exit counts.
            kotlinx.coroutines.delay(120)
            barHadFocus = false; NavSweep.active = false; NavSweep.tab = null
        }
    }
    // No first-entry exception: whatever tab holds focus when the dwell ends opens its page.
    val onTabFocusGained: () -> Boolean = { true }
    // One activation per tab per visit to the bar: a dwell-open followed by OK (or the reverse) must not navigate twice.
    var lastActivated by remember { mutableStateOf<JetflixTab?>(null) }
    val activate: (JetflixTab, Boolean) -> Unit = { tab, sweeping ->
        if (tab != selected && tab != lastActivated) {
            lastActivated = tab
            if (sweeping) { NavSweep.active = true; NavSweep.tab = tab }
            onTabSelected(tab)
        }
    }
    val openTab: (JetflixTab) -> Unit = { tab -> activate(tab, true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Arrived by moving across the tabs: the new page's selected tab takes focus so the sweep can continue.
        // Only the page the sweep was heading to takes it, and only once - a stale sweep must not steal focus.
        if (NavSweep.active) {
            if (NavSweep.tab == selected && selectedFocus != null) {
                repeat(15) {
                    try { selectedFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(40) }
                }
            }
            NavSweep.active = false; NavSweep.tab = null // never got the tab: do not leave content focus blocked
        }
    }
    Box(
        modifier = modifier
            .onFocusChanged {
                onNavFocusChange(it.hasFocus)
                barHasFocus = it.hasFocus
            }
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
        // Search is a magnifier icon at the start of the menu, the usual TV-menu pattern.
        SearchIconTab(
            selected = selected == JetflixTab.SEARCH,
            onClick = { activate(JetflixTab.SEARCH, false) },
            onFocusGained = onTabFocusGained, onDwell = { openTab(JetflixTab.SEARCH) },
            // On the Search page the magnifier is the selected tab, so Back from the page lands here.
            modifier = if (selected == JetflixTab.SEARCH && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
        )
        JetflixTab.entries.filter { it != JetflixTab.SEARCH }.forEach { tab ->
            NavTab(
                label = tab.label,
                selected = tab == selected,
                onClick = { activate(tab, false) },
                onFocusGained = onTabFocusGained, onDwell = { openTab(tab) },
                modifier = if (tab == selected && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier,
            )
        }
        Spacer(Modifier.weight(1f))
        if (userName != null) {
            // OK on your name opens the profile picker: switch profile or sign out.
            NavTab(
                label = userName,
                selected = false,
                onClick = onUserClick,
            )
        }
        }
    }
}

/** Runs [onFocused] once focus has rested on a tab briefly, so a quick sweep across several tabs does not open each page. */
@Composable
private fun FocusActivation(focused: Boolean, onFocusGained: () -> Boolean, onDwell: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused && onFocusGained()) { kotlinx.coroutines.delay(TAB_OPEN_DELAY_MS); onDwell() }
    }
}

const val TAB_OPEN_DELAY_MS = 150L

@Composable
private fun SearchIconTab(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocusGained: () -> Boolean = { false }, onDwell: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    FocusActivation(focused, onFocusGained, onDwell)
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
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
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocusLost: () -> Unit = {}, onFocusGained: () -> Boolean = { false }, onDwell: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    FocusActivation(focused, onFocusGained, onDwell)
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
