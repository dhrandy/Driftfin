package tv.jetflix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.NavSweep
import tv.jetflix.app.ui.components.TAB_OPEN_DELAY_MS
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Sweeping across the top tabs opens each page with focus on the tab. Content that loads after the
 * handoff must not pull focus down; the user enters content explicitly with Down or OK.
 * Reported on the TV: sweeping to Movies landed the selection on a movie, and Settings could never
 * be entered at all (OK on the selected tab was a no-op and the bar had no Down target there).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NavSweepFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val items = base.take(12).mapIndexed { i, it -> it.copy(Id = "swp$i", Name = "Title $i") }

    @Before fun resetSweep() { NavSweep.active = false; NavSweep.tab = null }
    @After fun clearSweep() { NavSweep.active = false; NavSweep.tab = null }

    private fun press(key: Key) { rule.onRoot().performKeyInput { pressKey(key) }; rule.waitForIdle() }

    private fun focusedLabel(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }?.takeIf { it.isNotEmpty() }?.let { return it }
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",")?.takeIf { it.isNotEmpty() }?.let { return it }
        return n.children.joinToString("|") { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    }

    private fun fieldFocused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        .any { it.config.contains(SemanticsProperties.EditableText) }

    /** Lets the sweep handoff retry loop finish so the target tab holds focus and the flag is cleared. */
    private fun settleSweep() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(700); rule.waitForIdle()
    }

    @Test fun sweepArrivalAtMoviesKeepsFocusOnTheTabWhenItemsLoad() {
        var state by mutableStateOf(LibraryUiState(loading = true))
        NavSweep.active = true; NavSweep.tab = JetflixTab.MOVIES
        rule.setContent { JetflixTheme { LibraryContent(state, repo, JetflixTab.MOVIES, "demo", {}, {}, {}) } }
        settleSweep()
        assertEquals("sweep handoff lands on the Movies tab", "Movies", focusedLabel())
        state = LibraryUiState(loading = false, items = items)
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals("items arriving after the sweep must not pull focus into the grid", "Movies", focusedLabel())
        press(Key.DirectionCenter)
        val entered = focusedLabel()
        assertTrue("OK on the selected tab must enter the content, still on: $entered", entered.isNotEmpty() && entered != "Movies")
    }

    @Test fun sweepArrivalAtSettingsThenOkBackAndDown() {
        NavSweep.active = true; NavSweep.tab = JetflixTab.SETTINGS
        rule.setContent { JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) } }
        settleSweep()
        assertEquals("sweep handoff lands on the Settings tab", "Settings", focusedLabel())
        press(Key.DirectionCenter)
        assertTrue("OK on the Settings tab must enter the content, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        assertEquals("Back from the content returns to the Settings tab", "Settings", focusedLabel())
        press(Key.DirectionDown)
        assertTrue("Down from the bar must enter the content, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
        // The whole list must be reachable, including the rows that start offscreen.
        val seen = mutableListOf<String>()
        repeat(20) {
            press(Key.DirectionDown)
            seen += focusedLabel()
        }
        assertTrue("Down must walk every Settings row to the bottom; visited: $seen", seen.any { it.contains("Sign out and forget") })
    }

    @Test fun sweepArrivalAtSearchThenOkAndDownReachTheField() {
        NavSweep.active = true; NavSweep.tab = JetflixTab.SEARCH
        rule.setContent { JetflixTheme { SearchContent(SearchUiState(), repo, "demo", {}, {}, {}, {}, {}) } }
        settleSweep()
        assertEquals("sweep handoff lands on the Search tab", "Search", focusedLabel())
        assertFalse("the search field must not take focus on sweep arrival", fieldFocused())
        press(Key.DirectionCenter)
        assertTrue("OK on the Search tab must focus the field", fieldFocused())
    }

    @Test fun sweepArrivalAtHomeKeepsFocusOnTheTabWhenTheHeroLoads() {
        val hero = base.first { it.Name == "The Gold Rush" }
        var state by mutableStateOf(HomeUiState(loading = true))
        NavSweep.active = true; NavSweep.tab = JetflixTab.HOME
        rule.setContent { JetflixTheme { HomeContent(state, repo, "demo", {}, {}, {}, {}, heroIntervalMs = 60000) } }
        settleSweep()
        assertEquals("sweep handoff lands on the Home tab", "Home", focusedLabel())
        state = HomeUiState(loading = false, hero = hero, heroes = listOf(hero), sections = listOf(HomeSection("Recently Added", items, wide = false)))
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        assertEquals("the hero arriving after the sweep must not pull focus off the tab", "Home", focusedLabel())
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        val afterOk = focusedLabel()
        assertTrue("OK on the Home tab must enter the content, was: $afterOk",
            afterOk.startsWith("Play") || afterOk.startsWith("Resume") || afterOk.startsWith("More"))
    }

    @Test fun nonSweepOpenStillTakesInitialFocus() {
        // 339c831's pinned behavior: pages opened without a sweep take their own initial content focus.
        rule.setContent { JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) } }
        rule.waitForIdle()
        assertTrue("Settings opened without a sweep still focuses its first row, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
    }
}
