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

    @Test fun okOnAPersonArmsAndItsReleaseOpensOnce() {
        val keys = PlayerKeys.CastKeySession()
        // The press itself opens nothing.
        assertEquals(CastStep.Consume, keys.onPress(KeyEvent.KEYCODE_DPAD_CENTER, 2, 4))
        assertEquals(CastStep.Open, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
        // The open is spent: a later stray release opens nothing and passes through.
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun aHeldOkStillOpensExactlyOnce() {
        val keys = PlayerKeys.CastKeySession()
        // Auto-repeat presses while held: each only re-arms.
        repeat(4) { assertEquals(CastStep.Consume, keys.onPress(KeyEvent.KEYCODE_DPAD_CENTER, 2, 4)) }
        assertEquals(CastStep.Open, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun enterKeysAllArmAndRelease() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val keys = PlayerKeys.CastKeySession()
            assertEquals(CastStep.Consume, keys.onPress(key, 0, 4))
            assertEquals(CastStep.Open, keys.onRelease(key))
        }
    }

    @Test fun leavingTheStripSwallowsThatKeysRelease() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK)) {
            val keys = PlayerKeys.CastKeySession()
            assertEquals(CastStep.Exit, keys.onPress(key, 2, 4))
            // The release is consumed, so closing the strip can never also trigger Back on the player.
            assertEquals(CastStep.Consume, keys.onRelease(key))
        }
    }

    @Test fun releasesTheStripNeverSawFallThrough() {
        val keys = PlayerKeys.CastKeySession()
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_BACK))
        // Moving around the strip arms nothing.
        keys.onPress(KeyEvent.KEYCODE_DPAD_LEFT, 2, 4)
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun resetDropsAnythingArmed() {
        val keys = PlayerKeys.CastKeySession()
        keys.onPress(KeyEvent.KEYCODE_DPAD_CENTER, 2, 4)
        keys.reset()
        assertEquals(null, keys.onRelease(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
