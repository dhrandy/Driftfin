package tv.jetflix

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.player.shouldKeepScreenOn

class KeepAwakeTest {
    @Test fun awakeWhilePlayingOrBuffering() {
        assertTrue(shouldKeepScreenOn(true, Player.STATE_READY))
        assertTrue(shouldKeepScreenOn(true, Player.STATE_BUFFERING))
    }
    @Test fun releasedWhenPausedEndedOrIdle() {
        assertFalse(shouldKeepScreenOn(false, Player.STATE_READY))
        assertFalse(shouldKeepScreenOn(true, Player.STATE_ENDED))
        assertFalse(shouldKeepScreenOn(true, Player.STATE_IDLE))
    }
}
