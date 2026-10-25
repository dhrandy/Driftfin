package tv.jetflix

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Back on the Search page: the first Back leaves the text field for the menu instead of doing nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchBackTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun fieldFocused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        .any { it.config.contains(SemanticsProperties.EditableText) }

    @Test fun backLeavesTheSearchFieldForTheMenu() {
        rule.setContent {
            JetflixTheme {
                SearchContent(SearchUiState(), JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        assertTrue("the search field starts focused", fieldFocused())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertFalse("Back moved focus out of the field", fieldFocused())
    }
}
