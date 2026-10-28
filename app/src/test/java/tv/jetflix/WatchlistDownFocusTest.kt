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
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.library.MySection
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Randy's emergency on build 24: on My Driftfin, select Watchlist and Down "can't select anything"
 * (Right was his workaround). The section row's Down hard-pointed at the sort row's focus requester,
 * which is detached when the sort row is hidden - a detached target swallows the move. Now Watchlist
 * shows the same Sort/Filter row as Favorites, and Down falls back to the first card otherwise.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class WatchlistDownFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    private val favs = (0 until 12).map { i -> base[i % base.size].copy(Id = "fv$i", Name = "Fav %02d".format(i), Type = "Movie") }
    private val starred = (0 until 12).map { i -> base[i % base.size].copy(Id = "wl$i", Name = "Watch %02d".format(i), Type = "Movie") }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun focusedText(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    @Test fun downFromWatchlistSectionRowReachesTheSortRowAndBack() {
        var state by mutableStateOf(LibraryUiState(loading = false, section = MySection.Favorites, items = favs))
        rule.setContent {
            JetflixTheme {
                LibraryContent(state, repo, JetflixTab.MY_LIST, "demo", {}, {}, {},
                    onSection = { sec -> state = state.copy(section = sec, items = if (sec == MySection.Watchlist) starred else favs) })
            }
        }
        rule.waitForIdle()
        // Converge to the top nav bar from wherever initial focus landed (Up is a dead end there).
        val tabs = setOf("Home", "Shows", "Movies", "My Driftfin", "Settings", "demo")
        repeat(8) { if (focusedText() !in tabs) press(Key.DirectionUp) }
        assertTrue("expected the nav bar, was: ${focusedText()}", focusedText() in tabs)
        press(Key.DirectionDown) // nav bar -> section row (selected Favorites)
        assertTrue("expected the section row, was: ${focusedText()}", focusedText().contains("Favorites") || focusedText().contains("Collections"))
        press(Key.DirectionRight) // -> Watchlist
        rule.waitForIdle()
        press(Key.Enter) // select Watchlist; focus stays on its button
        rule.waitForIdle()
        assertTrue("Watchlist should be the focused section, was: ${focusedText()}", focusedText().contains("Watchlist"))
        press(Key.DirectionDown) // the dead key on build 24
        rule.waitForIdle()
        assertTrue("Down must leave the Watchlist button for the sort row, was: ${focusedText()}", focusedText().startsWith("Sort:"))
        press(Key.DirectionUp) // and back up to the section row
        rule.waitForIdle()
        assertTrue("Up must return to the section row, was: ${focusedText()}", focusedText().contains("Watchlist"))
    }
}
