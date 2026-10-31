package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * Pixel captures of the rail-and-pane Settings under real D-pad focus: the Updates pane on open,
 * the Appearance pane after walking the rail, and the Account pane with its buttons.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    private fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun playbackPaneShowsPauseInfoToggle() {
        // Randy's toggle row: "Episode and movie info while paused" lives in the Playback pane, on by default.
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "Demo", "http://demo.test", {}, {}, {}, {}, {}, buildLabel = "Driftfin TV build 45") }
        }
        rule.waitForIdle()
        key(Key.DirectionLeft)                 // rail: Updates
        key(Key.DirectionDown)                 // Playback
        rule.onNodeWithText("Episode and movie info while paused").assertIsDisplayed()
        capture("settings-playback-pane")
    }

    @Test fun railBottomAccountFullyVisible() {
        // TV46 report: Account at the rail's bottom was slightly cut off. Walk the rail to
        // Account and capture it: the whole item must sit on screen, inside the rail card.
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "Demo", "http://demo.test", {}, {}, {}, {}, {}, buildLabel = "Driftfin TV build 45") }
        }
        rule.waitForIdle()
        key(Key.DirectionLeft)                 // rail: Updates
        repeat(7) { key(Key.DirectionDown) }   // Account
        // Two "Account" texts exist (rail item + previewed pane header); both must sit fully on screen.
        val accounts = rule.onAllNodesWithText("Account")
        accounts.assertCountEquals(2)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        accounts.fetchSemanticsNodes().forEach { n ->
            val b = n.boundsInRoot
            org.junit.Assert.assertTrue("Account fully on screen: $b within $root", b.top >= root.top && b.bottom <= root.bottom)
        }
        capture("settings-rail-account-bottom")
    }

    @Test fun railPanes() {
        rule.setContent {
            JetflixTheme { SettingsContent(true, true, true, "Demo", "http://demo.test", {}, {}, {}, {}, {}, buildLabel = "Driftfin TV build 45") }
        }
        rule.waitForIdle()
        capture("settings-updates-pane")
        key(Key.DirectionLeft)                 // rail: Updates
        repeat(3) { key(Key.DirectionDown) }   // Appearance
        capture("settings-appearance-rail")
        key(Key.DirectionRight)                // Poster size row
        capture("settings-appearance-pane")
        key(Key.DirectionLeft)
        repeat(4) { key(Key.DirectionDown) }   // Account
        key(Key.DirectionRight)                // Switch profile
        capture("settings-account-pane")
    }
}
