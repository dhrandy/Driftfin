package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
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

/** User request: "on the settings page, have a button to show the last updates - that way if you forget from the popup". */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class WhatsNewRowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun updatesPaneShowsWhatsNewRowAndItFires() {
        var clicked = false
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "demo", null, {}, {}, {}, {}, {}, onWhatsNewClick = { clicked = true }) }
        }
        rule.waitForIdle()
        // Opening focus is on "Check for updates"; Down reaches the replay row, OK fires it.
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertTrue("OK on the What's new row replays the card", clicked)
    }
}
