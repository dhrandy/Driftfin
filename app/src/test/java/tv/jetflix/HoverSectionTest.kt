package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
 * Randy on build 25: "just like the top navigation, on hover the favorites, watchlist, and
 * collections should change as you move across them" - no OK press. Driven by real D-pad keys.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HoverSectionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    @Test fun movingFocusAcrossSectionsSelectsThemWithoutOk() {
        val sections = mutableListOf<MySection>()
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            JetflixTheme {
                LibraryContent(
                    LibraryUiState(loading = false, items = items), repo, JetflixTab.MY_LIST, "demo", {}, {}, {},
                    onSection = { sections += it },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Favorites").requestFocus(); rule.waitForIdle()
        sections.clear() // focusing the already-selected section records the callback but is a no-op in the VM

        rule.onNodeWithText("Favorites").performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle()
        assertEquals(listOf(MySection.Watchlist), sections)

        rule.onNodeWithText("Watchlist").performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle()
        assertEquals(listOf(MySection.Watchlist, MySection.Collections), sections)

        rule.onNodeWithText("Collections").performKeyInput { pressKey(Key.DirectionLeft) }; rule.waitForIdle()
        assertEquals(listOf(MySection.Watchlist, MySection.Collections, MySection.Watchlist), sections)
    }
}
