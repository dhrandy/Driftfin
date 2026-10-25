package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Real D-pad key events through the real Home composable (Robolectric, no TV involved). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("demo-items.json")!!.readText(),
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

    private fun setHome(tabs: MutableList<String> = mutableListOf()) {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        val cont = items.take(6).map { it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }
        val state = HomeUiState(
            loading = false,
            hero = items.first { it.Name == "Jungle Book" },
            sections = listOf(
                HomeSection("Continue Watching", cont, wide = true),
                HomeSection("Recently Added Movies", items.drop(2), wide = false),
                HomeSection("Popular Picks", items.reversed(), wide = false),
            ),
        )
        rule.setContent { JetflixTheme { HomeContent(state, repo, "demo", { tabs += it.label }, {}, {}, {}) } }
        rule.waitForIdle()
        // Robolectric's window never reports focus, so the on-launch focus request cannot be observed here.
        // The first D-pad press gives the screen its initial focus on the hero Play button, as on a TV.
        if (zone() == "none") press(Key.DirectionDown)
    }

    private val navLabels = setOf("Home", "Shows", "Movies", "My List", "Search", "Settings", "demo")
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

    @Test fun startsOnHeroPlay() {
        setHome()
        assertEquals("hero", zone())
    }

    @Test fun downFromHeroEntersRowsAndUpComesBackToHero() {
        setHome()
        press(Key.DirectionDown); assertEquals("row", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
        press(Key.DirectionUp); press(Key.DirectionUp); assertEquals("hero", zone())
    }

    @Test fun navBarCanAlwaysGoDownAgain() {
        setHome()
        press(Key.DirectionUp); assertEquals("nav", zone())
        // Regression: Down from the nav bar used to leave focus stuck on the tab.
        press(Key.DirectionDown); assertEquals("hero", zone())
        press(Key.DirectionDown); assertEquals("row", zone())
        repeat(6) { press(Key.DirectionUp) }
        assertEquals("nav", zone())
        press(Key.DirectionDown); assertEquals("hero", zone())
    }

    @Test fun navBarSideKeysThenDownStillWorks() {
        setHome()
        press(Key.DirectionUp)
        repeat(4) { press(Key.DirectionRight) }
        assertEquals("nav", zone())
        press(Key.DirectionDown); assertEquals("hero", zone())
        press(Key.DirectionUp)
        repeat(6) { press(Key.DirectionRight) } // far right: user name
        press(Key.DirectionDown); assertEquals("hero", zone())
    }

    @Test fun backFromDeepInRowsGoesToTopThenExits() {
        setHome()
        press(Key.DirectionDown); press(Key.DirectionDown); press(Key.DirectionDown)
        assertEquals("row", zone())
        val dispatcher = rule.activity.onBackPressedDispatcher
        assertTrue("Back must be intercepted while in the rows", dispatcher.hasEnabledCallbacks())
        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("hero", zone())
        assertFalse("second Back must fall through so the app exits", dispatcher.hasEnabledCallbacks())
    }

    @Test fun trace() {
        setHome()
        println("NAV start: ${focusedText()}")
        repeat(3) { press(Key.DirectionDown); println("NAV down${it + 1}: ${focusedText()}") }
    }
}
