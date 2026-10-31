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

/**
 * TV46 report: "when on settings page and you get to the last thing on the right and click down, it goes
 * back to the settings left panel at the bottom. it shouldn't do anything. shouldn't go back to the left
 * panel unless you click left." Down on a pane's last row is a no-op; Left alone returns to the rail.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsDownBoundaryTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun focusedLabel(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
        return n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }?.takeIf { it.isNotEmpty() }
            ?: n.children.joinToString("|") { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    }

    @Test fun downOnLastUpdatesRowStaysPutAndLeftReturnsToRail() {
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) }
        }
        rule.waitForIdle()
        assertTrue("opening focus on Check for updates, was: ${focusedLabel()}", focusedLabel().contains("Check for updates"))
        press(Key.DirectionDown)   // What's new in this build
        assertTrue("Down reaches the What's new row, was: ${focusedLabel()}", focusedLabel().contains("What's new in this build"))
        press(Key.DirectionDown)   // Update notifications (last row)
        assertTrue("Down reaches the notifications row, was: ${focusedLabel()}", focusedLabel().contains("Update notifications"))
        press(Key.DirectionDown)   // boundary: must stay, not jump to the rail
        assertTrue("Down on the last row must stay put, was: ${focusedLabel()}", focusedLabel().contains("Update notifications"))
        press(Key.DirectionLeft)   // the only way back to the rail
        assertTrue("Left returns to the rail, was: ${focusedLabel()}", focusedLabel().contains("Updates"))
    }

    @Test fun downOnAccountActionsStaysPut() {
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) }
        }
        rule.waitForIdle()
        press(Key.DirectionLeft)                 // rail: Updates
        repeat(7) { press(Key.DirectionDown) }   // Account
        press(Key.DirectionRight)                // pane: Switch profile
        assertTrue("pane entry lands on Switch profile, was: ${focusedLabel()}", focusedLabel().contains("Switch profile"))
        press(Key.DirectionDown)                 // boundary: must stay on the account actions
        assertTrue("Down on the account actions must stay put, was: ${focusedLabel()}", focusedLabel().contains("Switch profile"))
    }
}
