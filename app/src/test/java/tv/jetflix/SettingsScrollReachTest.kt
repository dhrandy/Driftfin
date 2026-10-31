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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** The rail lists the groups and each pane its rows: every control must be D-pad reachable and stay on screen. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsScrollReachTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun press(key: Key) { rule.onRoot().performKeyInput { pressKey(key) }; rule.waitForIdle() }
    private fun focusedLabel(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
        return n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }?.takeIf { it.isNotEmpty() }
            ?: n.children.joinToString("|") { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    }
    private fun focusedBottom() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.bottom ?: -1f

    @Test fun railAndPanesReachEveryControlOnScreen() {
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) }
        }
        rule.waitForIdle()
        // The update row is first in Settings and takes the opening focus.
        assertTrue("opening focus should be on Check for updates, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height

        // Left enters the rail on the shown group; Down walks every group and previews its pane.
        press(Key.DirectionLeft)
        assertTrue("rail focused on Updates, was: ${focusedLabel()}", focusedLabel().contains("Updates"))
        val seen = mutableListOf<String>()
        repeat(7) {
            press(Key.DirectionDown)
            seen += focusedLabel()
            assertTrue("focused rail item below the screen: $seen", focusedBottom() <= rootH + 1f)
        }
        assertTrue("Down must walk the rail to Account; visited: $seen", seen.lastOrNull()?.contains("Account") == true)

        // The Account pane holds both profile actions, side by side.
        press(Key.DirectionRight)
        assertTrue("Account pane opens on Switch profile, was: ${focusedLabel()}", focusedLabel().contains("Switch profile"))
        assertTrue("Switch profile on screen", focusedBottom() <= rootH + 1f)
        press(Key.DirectionRight)
        assertTrue("Right from Switch profile should focus Sign out, was: ${focusedLabel()}", focusedLabel().contains("Sign out and forget"))
        assertTrue("Sign out on screen", focusedBottom() <= rootH + 1f)

        // Left from a pane control returns to the rail on the same group.
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertTrue("Left returns to the rail, was: ${focusedLabel()}", focusedLabel().contains("Account"))
    }
}
