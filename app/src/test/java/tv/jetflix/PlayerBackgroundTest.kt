package tv.jetflix

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.player.pausesForBackground

class PlayerBackgroundTest {
    @Test fun homeButtonBackgroundsPause() = assertTrue(pausesForBackground(Lifecycle.Event.ON_STOP))
    @Test fun transientOverlaysNeverPause() {
        // ON_PAUSE also fires for transient overlays; audio must stop only when the app truly leaves the screen.
        for (event in Lifecycle.Event.values()) if (event != Lifecycle.Event.ON_STOP) assertFalse(pausesForBackground(event))
    }
}
