package tv.jetflix

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.player.PlayerKeys
import tv.jetflix.app.ui.player.PlayerKeys.CastStep
import tv.jetflix.app.ui.player.PlayerKeys.DownAction

class PauseCastKeysTest {
    @Test fun castOnlyWhenPausedVisibleEnabledAndKnown() {
        assertTrue(PlayerKeys.castReady(paused = true, controlsVisible = true, settingOn = true, castCount = 3))
        assertFalse(PlayerKeys.castReady(false, true, true, 3))
        assertFalse(PlayerKeys.castReady(true, false, true, 3))
        assertFalse(PlayerKeys.castReady(true, true, false, 3))
        assertFalse(PlayerKeys.castReady(true, true, true, 0))
    }

    @Test fun firstDownStillOnlyRevealsControlsEvenWhenPaused() {
        assertEquals(DownAction.RevealControls, PlayerKeys.downAction(controlsVisible = false, repeatCount = 0, castReady = true))
    }

    @Test fun heldDownNeverEntersTheStrip() {
        assertEquals(DownAction.Ignore, PlayerKeys.downAction(true, 2, castReady = true))
    }

    @Test fun freshDownWhenPausedWithCastEntersTheStripOtherwiseOpensPanel() {
        assertEquals(DownAction.OpenCast, PlayerKeys.downAction(true, 0, castReady = true))
        assertEquals(DownAction.OpenPanel, PlayerKeys.downAction(true, 0, castReady = false))
    }

    @Test fun stripKeys() {
        assertEquals(CastStep.Move(0), PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_LEFT, 0, 4))
        assertEquals(CastStep.Move(1), PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_RIGHT, 0, 4))
        assertEquals(CastStep.Move(3), PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_RIGHT, 3, 4))
        assertEquals(CastStep.Open, PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_CENTER, 2, 4))
        assertEquals(CastStep.Open, PlayerKeys.castStep(KeyEvent.KEYCODE_ENTER, 2, 4))
        assertEquals(CastStep.Exit, PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_UP, 2, 4))
        assertEquals(CastStep.Exit, PlayerKeys.castStep(KeyEvent.KEYCODE_BACK, 2, 4))
        // Down, play/pause and seek keys are swallowed while the strip has focus, so nothing is skipped or resumed by accident.
        assertEquals(CastStep.Consume, PlayerKeys.castStep(KeyEvent.KEYCODE_DPAD_DOWN, 2, 4))
        assertEquals(CastStep.Consume, PlayerKeys.castStep(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 2, 4))
    }
}
