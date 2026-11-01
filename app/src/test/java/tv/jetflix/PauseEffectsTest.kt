package tv.jetflix

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.ui.player.PauseEffects
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** The opt-in pause frame effects ("Black and white while paused", "Blur while paused"):
 *  both default OFF, persist, gate on API 31, and only ever touch a paused frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class PauseEffectsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun key(k: androidx.compose.ui.input.key.Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Before fun resetPrefs() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("jetflix_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        AppSettings.init(ctx)
    }

    @Test fun bothEffectsDefaultOff() {
        assertFalse("pause_bw defaults off", AppSettings.pauseBw.value)
        assertFalse("pause_blur defaults off", AppSettings.pauseBlur.value)
    }

    @Test fun togglesPersist() {
        AppSettings.setPauseBw(true)
        AppSettings.setPauseBlur(true)
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(ctx.getSharedPreferences("jetflix_settings", android.content.Context.MODE_PRIVATE).getBoolean("pause_bw", false))
        assertTrue(ctx.getSharedPreferences("jetflix_settings", android.content.Context.MODE_PRIVATE).getBoolean("pause_blur", false))
        AppSettings.setPauseBw(false)
        assertFalse(AppSettings.pauseBw.value)
        AppSettings.setPauseBlur(false)
        assertFalse(AppSettings.pauseBlur.value)
    }

    @Test fun stateGatesOnSdkAndToggles() {
        // Below API 31 nothing applies and the player keeps its default surface.
        assertEquals(PauseEffects.State(false, false, 0f), PauseEffects.state(true, true, 30))
        // API 31+: effects only when their toggle is on; the texture surface only when an effect is on.
        assertEquals(PauseEffects.State(false, false, 0f), PauseEffects.state(false, false, 34))
        assertEquals(PauseEffects.State(true, true, 0f), PauseEffects.state(true, false, 34))
        assertEquals(PauseEffects.State(true, false, PauseEffects.BLUR_RADIUS), PauseEffects.state(false, true, 34))
        assertEquals(PauseEffects.State(true, true, PauseEffects.BLUR_RADIUS), PauseEffects.state(true, true, 34))
    }

    @Test fun effectOnlyAppliesWhilePaused() {
        val s = PauseEffects.state(true, true, 34)
        assertNull("playing frame stays untouched", PauseEffects.renderEffect(s, paused = false))
        assertNotNull("paused frame gets the effect", PauseEffects.renderEffect(s, paused = true))
        val off = PauseEffects.state(false, false, 34)
        assertNull("all toggles off means no effect even paused", PauseEffects.renderEffect(off, paused = true))
    }

    @Test fun playbackPaneShowsBothTogglesAndTheyFlipSettings() {
        rule.setContent {
            JetflixTheme {
                SettingsContent(
                    true, true, true, "Demo", "http://demo.test", {}, {}, {}, {}, {},
                    pauseBw = AppSettings.pauseBw.collectAsState().value,
                    onTogglePauseBw = { AppSettings.setPauseBw(!AppSettings.pauseBw.value) },
                    pauseBlur = AppSettings.pauseBlur.collectAsState().value,
                    onTogglePauseBlur = { AppSettings.setPauseBlur(!AppSettings.pauseBlur.value) },
                )
            }
        }
        rule.waitForIdle()
        // Same entry as the accepted pane test: D-pad into the rail, one down to Playback.
        // (initialCategory renders the pane but its rows are never placed, so they read zero-bounds.)
        key(androidx.compose.ui.input.key.Key.DirectionLeft)
        key(androidx.compose.ui.input.key.Key.DirectionDown)
        rule.onNodeWithText("Black and white while paused").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Blur while paused").performScrollTo().assertIsDisplayed()
        // Pixel evidence: both new rows visible in the Playback pane, both OFF by default.
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/settings-playback-pause-effects.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Black and white while paused").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("BW toggle flips the setting", AppSettings.pauseBw.value)
        rule.onNodeWithText("Blur while paused").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("Blur toggle flips the setting", AppSettings.pauseBlur.value)
    }
}
