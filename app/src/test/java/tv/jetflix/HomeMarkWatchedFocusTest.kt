package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.NavSweep
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The reported trigger, end to end: titles were marked watched from their pages, and back on Home
 * Down stopped working. Marking watched moves titles out of Continue Watching and Next Up on the next
 * quiet refresh; these tests follow that whole sequence - leave Home, come back, the refresh lands -
 * and no direction press may leave the screen focusless or the bar trapped.
 * Screen switches use a SaveableStateProvider keyed by the current screen, as MainActivity does.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeMarkWatchedFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("regression-items.json")!!.readText(),
        ).Items

    @After fun resetSettings() {
        AppSettings.setContinueWatchingPosters(false)
        NavSweep.active = false; NavSweep.tab = null
    }

    private fun focusedText(): String {
        val nodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        return nodes.joinToString(" || ") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
        }
    }

    private fun press(key: Key) {
        rule.onAllNodes(isRoot())[0].performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun long(prefix: String, n: Int) = (0 until n).map { i -> items[i % items.size].copy(Id = prefix + i) }

    private fun sections(): List<HomeSection> {
        val cont = long("c", 40).map { it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }
        return listOf(
            HomeSection("Continue Watching", cont, wide = true),
            HomeSection("Next Up", long("n", 40), wide = true),
            HomeSection("Recently Added Movies", long("m", 40), wide = false),
            HomeSection("Recently Added Episodes", long("e", 40), wide = true),
            HomeSection("Favorites", long("f", 40), wide = false),
            HomeSection("Popular Picks", long("p", 40), wide = false),
        )
    }

    private val navLabels = setOf("Home", "Shows", "Movies", "My Stuff", "Search", "Settings", "demo")
    private val heroLabels = setOf("Play", "Resume", "Play from start", "More Info")
    private fun zone(): String {
        val t = focusedText()
        return when {
            t.isEmpty() -> "none"
            t in navLabels -> "nav"
            t in heroLabels || t.startsWith("Play") || t.startsWith("Resume") || t.startsWith("More") -> "hero"
            else -> "row"
        }
    }

    private var screen by mutableStateOf("home")
    private var homeState by mutableStateOf(HomeUiState())

    private fun setHome(withHero: Boolean = true) {
        screen = "home"
        homeState = HomeUiState(
            loading = false,
            hero = if (withHero) items.first { it.Name == "The Gold Rush" } else null,
            sections = sections(),
        )
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                val holder = rememberSaveableStateHolder()
                holder.SaveableStateProvider(screen) {
                    if (screen == "home") {
                        HomeContent(homeState, repo, "demo", { }, { }, { }, { }, heroIntervalMs = 1000)
                    } else {
                        Box(Modifier.fillMaxSize().focusable())
                    }
                }
            }
        }
        rule.waitForIdle()
        if (zone() == "none") press(Key.DirectionDown)
    }

    /** The quiet refresh after marking watched: these titles are gone from their rows now. */
    private fun dropFromRow(row: Int, count: Int) {
        homeState = homeState.copy(
            sections = homeState.sections.mapIndexed { i, s ->
                if (i == row) s.copy(items = s.items.drop(count)) else s
            }.filter { it.items.isNotEmpty() },
        )
        rule.waitForIdle()
    }

    @Test fun downFromBarRecoversAfterWatchedItemsLeaveOnTabReturn() {
        setHome(withHero = true)
        repeat(4) { press(Key.DirectionDown) }
        assertEquals("row", zone())
        // Off to a library screen; Back from a tab lands on the Home tab with the old scroll spot kept.
        screen = "library"
        rule.waitForIdle()
        NavSweep.active = true
        NavSweep.tab = JetflixTab.HOME
        screen = "home"
        rule.waitForIdle()
        assertEquals("Back-to-Home lands on the Home tab", "nav", zone())
        // The quiet refresh now drops the just-watched titles from Continue Watching.
        dropFromRow(0, 3)
        assertEquals("nav", zone())
        press(Key.DirectionDown)
        assertNotEquals("Down from the bar must move focus after the refresh", "nav", zone())
        assertNotEquals("none", zone())
        press(Key.DirectionDown)
        assertEquals("row", zone())
    }

    @Test fun focusedCardRemovedFromContinueWatchingDoesNotTrap() {
        setHome(withHero = true)
        press(Key.DirectionDown) // hero -> Continue Watching's first card
        assertEquals("row", zone())
        // The card under focus is marked watched elsewhere and the quiet refresh removes it.
        dropFromRow(0, 1)
        press(Key.DirectionDown)
        assertNotEquals("no direction press may leave Home focusless", "none", zone())
        press(Key.DirectionUp)
        assertNotEquals("none", zone())
        press(Key.DirectionDown)
        assertNotEquals("none", zone())
    }

    @Test fun focusedCardRemovedFromNextUpWithPostersDoesNotTrap() {
        // Poster cards make the rows taller, so from Next Up the banner is fully scrolled away:
        // the enter guard's banner target is out of composition when the focused card disappears.
        AppSettings.setContinueWatchingPosters(true)
        setHome(withHero = true)
        press(Key.DirectionDown) // -> Continue Watching
        press(Key.DirectionDown) // -> Next Up
        assertEquals("row", zone())
        dropFromRow(1, 1) // the focused Next Up card was just marked watched and leaves the row
        press(Key.DirectionDown)
        assertNotEquals("Down must not die when the focused card was removed while the banner is scrolled away", "none", zone())
        press(Key.DirectionUp)
        assertNotEquals("none", zone())
    }

    @Test fun continueWatchingRowVanishingWhileNextUpFocusedKeepsFocus() {
        setHome(withHero = true)
        press(Key.DirectionDown) // -> Continue Watching
        press(Key.DirectionDown) // -> Next Up
        assertEquals("row", zone())
        // Everything in Continue Watching was marked watched: the whole row leaves, Next Up shifts up.
        dropFromRow(0, 40)
        assertEquals("focus on an untouched row must survive a row above it leaving", "row", zone())
        press(Key.DirectionUp)
        assertNotEquals("none", zone())
    }

    @Test fun detailsReturnRestoresCardThenRefreshMovesOn() {
        setHome(withHero = true)
        press(Key.DirectionDown) // -> Continue Watching's first card; the card is remembered
        assertEquals("row", zone())
        // Details page sits on top (no tab handoff on the way back, unlike a library screen).
        screen = "details"
        rule.waitForIdle()
        screen = "home"
        rule.waitForIdle()
        assertEquals("Back from details restores the remembered card", "row", zone())
        // The title was marked watched on the details page; the quiet refresh now removes it.
        dropFromRow(0, 1)
        press(Key.DirectionDown)
        assertNotEquals("none", zone())
    }
}
