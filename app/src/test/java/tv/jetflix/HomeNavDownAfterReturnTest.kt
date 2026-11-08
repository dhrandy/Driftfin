package tv.jetflix

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
 * Regression for the reported focus trap: after something played and Home came back, focus sat on the
 * Home tab and Down did nothing (sideways still worked). Returning to Home restores the old scroll spot,
 * so the banner stays scrolled out of composition; the bar's Down target pointed at that uncomposed
 * banner (or at the uncomposed first row when there is no banner) and the move silently went nowhere.
 * Down must now scroll the list back and land on the target instead of trapping the bar.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeNavDownAfterReturnTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("regression-items.json")!!.readText(),
        ).Items

    private fun focusedText(): String {
        val nodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        return nodes.joinToString(" || ") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
        }
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
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

    // The overlay stands in for a page that sits on top of Home (Search, a trailer, the screensaver):
    // Home leaves composition and comes back with its scroll spot restored, so the banner is not
    // composed. NavSweep mimics the app's Back-to-Home handoff, which lands focus on the Home tab.
    private var overlay by mutableStateOf(false)
    private var homeState by mutableStateOf(HomeUiState())

    private fun setHome(withHero: Boolean = true) {
        overlay = false
        homeState = HomeUiState(
            loading = false,
            hero = if (withHero) items.first { it.Name == "The Gold Rush" } else null,
            sections = sections(),
        )
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                val holder = rememberSaveableStateHolder()
                holder.SaveableStateProvider("home") {
                    if (!overlay) {
                        HomeContent(homeState, repo, "demo", { }, { }, { }, { }, heroIntervalMs = 1000)
                    }
                }
            }
        }
        rule.waitForIdle()
        if (zone() == "none") press(Key.DirectionDown)
    }

    /** Browse deep into the rows, leave Home under an overlay, then come back to the Home tab. */
    private fun leaveAndReturnScrolled() {
        repeat(4) { press(Key.DirectionDown) }
        assertEquals("row", zone())
        overlay = true
        rule.waitForIdle()
        NavSweep.active = true
        NavSweep.tab = JetflixTab.HOME
        overlay = false
        rule.waitForIdle()
        assertEquals("Back-to-Home lands on the Home tab", "nav", zone())
    }

    @Test fun downFromBarRecoversWhenBannerScrolledOutOfComposition() {
        setHome(withHero = true)
        leaveAndReturnScrolled()
        press(Key.DirectionDown)
        assertEquals("Down from the bar must not trap focus when the banner is scrolled away", "hero", zone())
        press(Key.DirectionDown)
        assertEquals("row", zone())
    }

    @Test fun downFromBarRecoversToFirstRowWhenNoBanner() {
        setHome(withHero = false)
        leaveAndReturnScrolled()
        press(Key.DirectionDown)
        assertEquals("Down from the bar must reach the first row when there is no banner", "row", zone())
    }

    @Test fun downFromBarRecoversAfterWatchedItemDisappearsFromRow() {
        setHome(withHero = true)
        leaveAndReturnScrolled()
        // The just-watched movie leaving Continue Watching is the reported trigger; the trap must not return.
        homeState = homeState.copy(
            sections = homeState.sections.mapIndexed { i, s ->
                if (i == 0) s.copy(items = s.items.drop(1)) else s
            },
        )
        rule.waitForIdle()
        assertEquals("nav", zone())
        press(Key.DirectionDown)
        assertEquals("hero", zone())
    }

    @Test fun okOnSelectedTabAlsoRecoversWhenBannerScrolledAway() {
        setHome(withHero = true)
        leaveAndReturnScrolled()
        press(Key.DirectionCenter)
        assertEquals("OK on the selected tab enters the page like Down", "hero", zone())
    }
}
