package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.ui.player.PauseEffects
import tv.jetflix.app.ui.settings.SettingsCategory
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Capability gate truth table. Both pause effects (black-and-white and blur) are RenderEffect-based,
 *  so BOTH need Android 12 (API 31) plus a TextureView surface - the threshold is identical per effect
 *  because grayscale uses RenderEffect.createColorFilterEffect, not a lower-API pipeline. Below 31 the
 *  state must be a complete no-op so an enabled-looking toggle never silently does nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PauseEffectsStateTest {
    @Test fun supportedOnlyFromAndroid12() {
        assertFalse(PauseEffects.supported(25)) // Fire OS 6 stick (the reported TV)
        assertFalse(PauseEffects.supported(30))
        assertTrue(PauseEffects.supported(31))
        assertTrue(PauseEffects.supported(34))
    }

    @Test fun unsupportedDeviceIsFullNoOp() {
        val s = PauseEffects.state(bwOn = true, blurOn = true, sdkInt = 25)
        assertFalse("no TextureView switch on unsupported devices", s.textureSurface)
        assertFalse(s.grayscale)
        assertEquals(0f, s.blurRadius)
    }

    @Test fun supportedDeviceHonorsEachToggleIndependently() {
        PauseEffects.state(bwOn = true, blurOn = false, sdkInt = 31).let {
            assertTrue(it.textureSurface); assertTrue(it.grayscale); assertEquals(0f, it.blurRadius)
        }
        PauseEffects.state(bwOn = false, blurOn = true, sdkInt = 31).let {
            assertTrue(it.textureSurface); assertFalse(it.grayscale); assertEquals(PauseEffects.BLUR_RADIUS, it.blurRadius)
        }
        PauseEffects.state(bwOn = false, blurOn = false, sdkInt = 31).let {
            assertFalse(it.textureSurface); assertFalse(it.grayscale); assertEquals(0f, it.blurRadius)
        }
    }
}

/** The Settings rows for the two pause effects: on an unsupported device they must be visibly dimmed,
 *  carry the short reason, stay out of the DPAD chain and ignore clicks, while the stored preference is
 *  untouched (the dimmed switch still shows it). On a supported device they keep working as toggles. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PauseEffectsGateUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun open(supported: Boolean, bwToggles: () -> Unit = {}) {
        rule.setContent {
            JetflixTheme {
                SettingsContent(
                    true, true, true, "demo", null, {}, {}, {}, {}, {},
                    initialCategory = SettingsCategory.Playback,
                    pauseBw = true, // stored preference: preserved and still shown, dimmed
                    onTogglePauseBw = bwToggles,
                    pauseEffectsSupported = supported,
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
    }

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
    }

    private fun focusedLabel(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return ""
        return n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text }?.takeIf { it.isNotEmpty() }
            ?: n.children.joinToString("|") { c -> c.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: "?" }
    }

    private fun screenBitmap(): Bitmap {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    @Test fun unsupportedDeviceRowsAreDimmedInertAndOutOfTheFocusChain() {
        open(supported = false)
        // Render for CI inspection BEFORE any assertion.
        val bmp = screenBitmap()
        val out = java.io.File("build/outputs/qa/settings-pausefx-unsupported.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Both rows show the short reason, not the full description.
        rule.onAllNodesWithText("Needs Android 12 or newer.").assertCountEquals(2)
        rule.onNodeWithText("Turns the paused video frame black and white. Off by default.", substring = true).assertDoesNotExist()

        // No click action anywhere on either row.
        rule.onNodeWithText("Black and white while paused").assert(!hasClickAction())
        rule.onNodeWithText("Blur while paused").assert(!hasClickAction())

        // DPAD through the pane: focus starts on the first Playback row, must skip both disabled rows
        // and still reach "Skip Intro button" below them.
        assertTrue("opening focus on the pane's first row, was: ${focusedLabel()}", focusedLabel().contains("Autoplay next episode"))
        val seen = mutableListOf<String>()
        var reachedSkipIntro = false
        for (i in 1..8) {
            seen += focusedLabel()
            if (focusedLabel().contains("Skip Intro")) { reachedSkipIntro = true; break }
            press(Key.DirectionDown)
        }
        assertTrue("disabled rows must never take focus, visited: $seen",
            seen.none { it.contains("Black and white") || it.contains("Blur while paused") })
        assertTrue("Skip Intro (below the disabled rows) stays reachable, visited: $seen", reachedSkipIntro)
    }

    @Test fun supportedDeviceRowsStayNormalToggles() {
        var bwClicks = 0
        open(supported = true, bwToggles = { bwClicks++ })
        rule.onNodeWithText("Black and white while paused").assert(hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals("supported device keeps the toggle working", 1, bwClicks)
        rule.onNodeWithText("Blur while paused").assert(hasClickAction())
        // The full description is shown when the device can run the effect.
        rule.onNodeWithText("Turns the paused video frame black and white. Off by default.").assertExists()
    }
}
