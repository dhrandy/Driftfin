package tv.jetflix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Randy on build 21, with a photo: scrolled down on Shows, Back goes to the menu bar (good), sweep to
 * the next tab, and Down can't get back into the grid - the grid comes back still scrolled, the sort
 * row is offscreen and disposed, and the bar's hard-wired Down target is gone with it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryDownReentryTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val shows = (0 until 24).map { i -> base[i % base.size].copy(Id = "sh$i", Name = "Show %02d".format(i), Type = "Series") }
    private val movies = (0 until 24).map { i -> base[i % base.size].copy(Id = "mv$i", Name = "Movie %02d".format(i), Type = "Movie") }

    @Before fun resetSweep() { NavSweep.active = false; NavSweep.tab = null }
    @After fun clearSweep() { NavSweep.active = false; NavSweep.tab = null }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    /** A held key: one down plus repeats and a key-up, in a single dispatch (fast deep-scroll). */
    private fun holdDown(repeats: Int) {
        rule.runOnUiThread {
            for (r in 0..repeats) rule.activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN, r))
            rule.activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0))
        }
        rule.waitForIdle()
    }
    private fun dwell() { rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle() }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun focusedText(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    @Test fun downFromTheBarReentersAScrolledRestoredGrid() {
        var tab by mutableStateOf(JetflixTab.SHOWS)
        rule.setContent {
            JetflixTheme {
                val stateHolder = rememberSaveableStateHolder()
                stateHolder.SaveableStateProvider(tab.toString()) {
                    when (tab) {
                        JetflixTab.SHOWS -> LibraryContent(LibraryUiState(loading = false, items = shows), repo, JetflixTab.SHOWS, "demo", { tab = it }, {}, {})
                        JetflixTab.MOVIES -> LibraryContent(LibraryUiState(loading = false, items = movies), repo, JetflixTab.MOVIES, "demo", { tab = it }, {}, {})
                        else -> {}
                    }
                }
            }
        }
        rule.waitForIdle()

        // Scroll Shows deep: bar -> sort row -> grid, then a held Down scrolls the grid well past the sort row.
        press(Key.DirectionDown) // sort row
        press(Key.DirectionDown) // first card row
        holdDown(6)
        val deepCard = focusedText()
        assertNotEquals("must be inside the grid", "Shows", deepCard)

        // Back to the bar (the behavior Randy likes), then sweep right to Movies.
        back()
        press(Key.DirectionRight) // Movies tab focus
        dwell() // dwell opens Movies, sweep handoff lands on its tab
        // Movies is fresh at the top: Down enters via the sort row (control case).
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        // Scroll Movies deep as well, then go back to the bar and sweep left to the SCROLLED Shows grid.
        holdDown(6)
        back()
        press(Key.DirectionLeft) // Shows tab focus
        dwell() // sweep opens Shows again; its grid restores the scrolled position
        // The reported bug: Down from the Shows tab is dead here.
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals("Down from the bar must re-enter the restored scrolled grid on the remembered card", deepCard, focusedText())
    }
}
