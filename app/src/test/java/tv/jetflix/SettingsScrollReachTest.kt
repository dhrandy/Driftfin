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
        val seen = mutableListOf<String>()
        var lastBottom = 0f
        repeat(20) {
            press(Key.DirectionDown)
            val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return@repeat
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.children.joinToString { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
            seen += label
            lastBottom = n.boundsInRoot.bottom
        }
        assertTrue("never focused sign out; focus visited: $seen", seen.any { it.contains("Sign out and forget") })
        assertTrue("never focused Check for updates (the last row); focus visited: $seen", seen.any { it.contains("Check for updates") })
        val rootH = rule.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue("focused row is below the visible area: bottom=$lastBottom of $rootH; visited $seen", lastBottom <= rootH + 1f)
    }
}
