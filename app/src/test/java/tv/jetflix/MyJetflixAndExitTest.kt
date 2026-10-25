package tv.jetflix

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ExitDialog
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.library.MySection
import tv.jetflix.app.ui.theme.JetflixTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class MyJetflixAndExitTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    @Test fun myJetflixHasCollectionsFavoritesAndDirection() {
        val sections = mutableListOf<MySection>()
        val reversed = mutableListOf<Boolean>()
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                LibraryContent(
                    LibraryUiState(loading = false, items = items), repo, JetflixTab.MY_LIST, "demo", {}, {}, {},
                    onReverse = { reversed += it }, onSection = { sections += it },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Collections").assertIsDisplayed()
        rule.onNodeWithText("Favorites").assertIsDisplayed()
        rule.onNodeWithText("Collections").requestFocus(); rule.waitForIdle(); rule.onNodeWithText("Collections").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(listOf(MySection.Collections), sections)
        rule.onNodeWithText("Ascending").requestFocus(); rule.waitForIdle(); rule.onNodeWithText("Ascending").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(listOf(true), reversed)
    }

    @Test fun exitDialogCancelStaysAndExitLeaves() {
        var cancelled = 0; var exited = 0
        rule.setContent { JetflixTheme { ExitDialog(onCancel = { cancelled++ }, onExit = { exited++ }) } }
        rule.waitForIdle()
        rule.onNodeWithText("Exit Driftfin?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsFocused()
        rule.onNodeWithText("Cancel").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(1, cancelled); assertEquals(0, exited)
        rule.onNodeWithText("Exit").requestFocus(); rule.waitForIdle(); rule.onNodeWithText("Exit").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals(1, exited)
    }

    @Test fun filterRowOffersWatchedGenreAndYearAndSortHasPlayCount() {
        val calls = mutableListOf<String>()
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                LibraryContent(
                    LibraryUiState(loading = false, items = items, genre = "Drama", year = 1990, genres = listOf("Action", "Drama", "Western"), years = listOf(2001, 1995, 1990)),
                    repo, JetflixTab.MOVIES, "demo", {}, {}, {},
                    onWatch = { calls += "watch" }, onGenre = { calls += "genre=$it" }, onYear = { calls += "year=$it" },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Play Count").assertIsDisplayed()
        rule.onNodeWithText("Genre: Drama").assertIsDisplayed()
        rule.onNodeWithText("Year: 1990").assertIsDisplayed()
        fun enter(label: String) { rule.onNodeWithText(label).requestFocus(); rule.waitForIdle(); rule.onNodeWithText(label).performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle() }
        enter("Watched: All")
        // Pickers list every value the server returned for the library, not just values in the current result.
        enter("Genre: Drama"); rule.onNodeWithText("Western").assertIsDisplayed(); enter("Western")
        enter("Year: 1990"); rule.onNodeWithText("2001").assertIsDisplayed(); enter("1995")
        assertEquals(listOf("watch", "genre=Western", "year=1995"), calls)
    }
}
