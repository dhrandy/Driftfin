package tv.jetflix

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Up from the top of Settings or Search must land on that page's own tab, not on Search or another tab. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NavUpReturnTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var fm: FocusManager? = null

    private fun texts(n: SemanticsNode): List<String> =
        n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + n.children.flatMap { texts(it) }
    private fun focused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().flatMap { texts(it) }
    private fun move(d: FocusDirection) { rule.runOnUiThread { fm!!.moveFocus(d) }; rule.waitForIdle() }
    private val tabs = listOf("Home", "Shows", "Movies", "My Stuff", "Settings")

    @Test fun settingsUpFromPaneAndRailLandsOnSettingsTab() {
        rule.setContent { JetflixTheme { fm = LocalFocusManager.current; SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) } }
        rule.waitForIdle()
        assertTrue("opens on Check for updates: ${focused()}", focused().any { it.contains("Check for updates") })
        move(FocusDirection.Up)
        assertEquals("pane Up", listOf("Settings"), focused().filter { it in tabs })
        move(FocusDirection.Down)
        assertTrue("Down re-enters the pane: ${focused()}", focused().any { it.contains("Check for updates") })
        move(FocusDirection.Left)
        assertTrue("Left lands on the rail: ${focused()}", focused().any { it.contains("Updates") })
        move(FocusDirection.Up)
        assertEquals("rail Up", listOf("Settings"), focused().filter { it in tabs })
    }

    @Test fun searchUpFromFieldLeavesFieldAndNeverLandsOnAnotherTab() {
        rule.setContent {
            JetflixTheme {
                fm = LocalFocusManager.current
                SearchContent(SearchUiState(query = "tom", results = listOf(BaseItemDto(Id = "m1", Name = "Big", Type = "Movie"))), JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        move(FocusDirection.Up)
        val f = focused()
        assertTrue("Up must not land on a non-Search tab: $f", f.none { it in tabs })
        assertTrue("Up left the field and sits on the nav bar", rule.onAllNodes(isFocused()).fetchSemanticsNodes().none { it.config.contains(SemanticsProperties.EditableText) })
    }
}
