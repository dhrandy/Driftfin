package tv.jetflix

import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibrarySort
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Sort row on the Shows/Movies grid, driven by real D-pad keys (Robolectric, no TV involved). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LibrarySortTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun focused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString("||") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?"
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun sortRowIsReachableAndOkPicksAnOrder() {
        val picked = mutableListOf<LibrarySort>()
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                LibraryContent(LibraryUiState(loading = false, items = items), repo, JetflixTab.MOVIES, "demo", {}, {}, {}, onSort = { picked += it })
            }
        }
        rule.waitForIdle()
        val navLabels = setOf("Home", "Shows", "Movies", "My Driftfin", "Search", "Settings", "demo")
        val sortLabels = listOf("Sort: Name \u2191", "Filter")
        if (focused().isEmpty()) press(Key.DirectionDown) // Robolectric has no window focus; first key lands on the header
        if (focused() in sortLabels) { press(Key.DirectionUp); press(Key.DirectionUp) }
        assertTrue("header focused, was ${focused()}", focused() in navLabels || focused() == "?")
        press(Key.DirectionDown)
        assertTrue("header Down reaches the sort row, was: ${focused()}", focused() in sortLabels)
        press(Key.Enter) // opens the Sort panel
        rule.onNodeWithText("Rating").assertIsDisplayed()
        rule.onNodeWithText("Rating").requestFocus(); rule.waitForIdle(); rule.onNodeWithText("Rating").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(listOf(LibrarySort.Rating), picked)
        press(Key.DirectionDown)
        assertTrue("grid focused, was ${focused()}", focused() !in sortLabels && focused() !in navLabels)
        press(Key.DirectionUp)
        assertTrue("Up from the grid returns to the sort row, was ${focused()}", focused() in sortLabels)
        press(Key.DirectionUp)
        assertTrue("then the header, was ${focused()}", rule.onAllNodes(isFocused()).fetchSemanticsNodes().single().boundsInRoot.top < 120f)
    }

    @Test fun backFromTheGridGoesToTheTopMenu() {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme { LibraryContent(LibraryUiState(loading = false, items = items), repo, JetflixTab.MOVIES, "demo", {}, {}, {}) }
        }
        rule.waitForIdle()
        press(Key.DirectionDown) // header
        press(Key.DirectionDown) // sort row
        press(Key.DirectionDown) // grid
        assertTrue("on grid, was ${focused()}", focused() == "?")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("Back lands on the selected tab", "Movies", focused())
    }
}
