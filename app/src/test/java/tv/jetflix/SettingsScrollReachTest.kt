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

/** The Settings list is taller than a 540dp screen: Down must reach the last row and keep it on screen. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsScrollReachTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun press(key: Key) { rule.onRoot().performKeyInput { pressKey(key) }; rule.waitForIdle() }

    @Test fun downReachesSignOutAndItStaysOnScreen() {
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}) }
        }
        rule.waitForIdle()
        // The update row is first in Settings and takes the opening focus.
        val firstNode = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()
        val firstLabel = firstNode?.config?.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }
            ?: firstNode?.children?.joinToString { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" } ?: ""
        assertTrue("opening focus should be on Check for updates, was: $firstLabel", firstLabel.contains("Check for updates"))
        val seen = mutableListOf<String>()
        var lastBottom = 0f
        repeat(20) {
            press(Key.DirectionDown)
            val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return@repeat
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.children.joinToString { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
            seen += label
            lastBottom = n.boundsInRoot.bottom
        }
        // The account buttons are side by side in one pinned footer row: Down from the last settings row
        // lands on one of them (which one is spatial-search's choice), and Left/Right moves between them.
        assertTrue("never focused the account footer; focus visited: $seen", seen.any { it.contains("Switch profile") || it.contains("Sign out and forget") })
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue("focused row is below the visible area: bottom=$lastBottom of $rootH; visited $seen", lastBottom <= rootH + 1f)
        // Walk to "Switch profile" if we are not there, then Right must land on Sign out.
        var guard = 0
        fun footerLabel(): String {
            val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
            return n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: ""
        }
        while (!footerLabel().contains("Switch profile") && guard++ < 2) { press(Key.DirectionLeft) }
        assertTrue("Switch profile reachable in the footer, was: ${footerLabel()}", footerLabel().contains("Switch profile"))
        press(Key.DirectionRight)
        val label = footerLabel()
        assertTrue("Right from Switch profile should focus Sign out, was: $label", label.contains("Sign out and forget"))
        val bottom = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()!!.boundsInRoot.bottom
        assertTrue("sign out is below the visible area: bottom=$bottom of $rootH", bottom <= rootH + 1f)
    }
}
