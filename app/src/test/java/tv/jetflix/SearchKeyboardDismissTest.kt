package tv.jetflix

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performImeAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The user on build 58: pressing the on-screen keyboard's search key ran the search but left the
 * keyboard visible until Back. The IME action must submit and dismiss the keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchKeyboardDismissTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun imeSearchSubmitsAndHidesTheKeyboard() {
        var searched = 0
        var hidden = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() {}
            override fun hide() { hidden++ }
        }
        rule.setContent {
            JetflixTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    SearchContent(
                        state = SearchUiState(query = "dune"),
                        repo = JellyfinRepository().apply { apiFor("http://demo.test") },
                        userName = "demo",
                        onQueryChange = {},
                        onSearchNow = { searched++ },
                        onTabSelected = {},
                        onItemClick = {},
                        onUserClick = {},
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()

        assertEquals("the search ran", 1, searched)
        assertEquals("the keyboard was dismissed", 1, hidden)
    }
}
