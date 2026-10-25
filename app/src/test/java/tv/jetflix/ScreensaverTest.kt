package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.IdleScreensaverHost
import tv.jetflix.app.ui.theme.JetflixTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ScreensaverTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun host(blocked: Boolean = false, urls: List<String>, clicks: IntArray) = rule.setContent {
        JetflixTheme {
            IdleScreensaverHost(blocked = blocked, loadUrls = { urls }, idleMs = 1000) {
                androidx.tv.material3.Button(onClick = { clicks[0]++ }) { androidx.tv.material3.Text("Underneath") }
            }
        }
    }

    @Test fun startsAfterIdleAndFirstKeyOnlyWakesIt() {
        val clicks = IntArray(1)
        host(urls = listOf("http://demo.test/Items/a/Images/Backdrop"), clicks = clicks)
        rule.onNodeWithText("Underneath").requestFocus()
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        rule.onNodeWithContentDescription("Screensaver").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.onNodeWithContentDescription("Screensaver").assertExists()
        rule.onNodeWithText("Underneath").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals("the waking key is swallowed", 0, clicks[0])
        rule.onNodeWithContentDescription("Screensaver").assertDoesNotExist()
        rule.onNodeWithText("Underneath").performKeyInput { pressKey(Key.Enter) }; rule.waitForIdle()
        assertEquals("next key reaches the app", 1, clicks[0])
    }

    @Test fun neverStartsWhenBlockedOrWithoutArt() {
        host(blocked = true, urls = listOf("http://demo.test/x"), clicks = IntArray(1))
        rule.mainClock.advanceTimeBy(5000); rule.waitForIdle()
        rule.onNodeWithContentDescription("Screensaver").assertDoesNotExist()
    }

    @Test fun staysOffWhenThereIsNoEligibleArt() {
        host(urls = emptyList(), clicks = IntArray(1))
        rule.mainClock.advanceTimeBy(5000); rule.waitForIdle()
        rule.onNodeWithContentDescription("Screensaver").assertDoesNotExist()
    }
}
