package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.player.PlayerKeys
import tv.jetflix.app.ui.player.PlayerKeys.DownAction

class PlayerDownKeyTest {
    @Test fun firstDownWithHiddenControlsOnlyReveals() {
        assertEquals(DownAction.RevealControls, PlayerKeys.downAction(controlsVisible = false, repeatCount = 0))
    }

    @Test fun heldKeyRepeatsNeverOpenThePanel() {
        // The press that revealed the controls keeps auto-repeating; the controls are now visible but the repeats must not act.
        assertEquals(DownAction.Ignore, PlayerKeys.downAction(controlsVisible = true, repeatCount = 1))
        assertEquals(DownAction.Ignore, PlayerKeys.downAction(controlsVisible = true, repeatCount = 7))
    }

    @Test fun repeatsWhileStillHiddenKeepRevealing() {
        assertEquals(DownAction.RevealControls, PlayerKeys.downAction(controlsVisible = false, repeatCount = 3))
    }

    @Test fun freshDownWithVisibleControlsOpensThePanel() {
        assertEquals(DownAction.OpenPanel, PlayerKeys.downAction(controlsVisible = true, repeatCount = 0))
    }
}
