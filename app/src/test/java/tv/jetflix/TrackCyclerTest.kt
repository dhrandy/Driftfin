package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.player.TrackCycler

class TrackCyclerTest {
    @Test fun audioWrapsAround() {
        assertEquals(1, TrackCycler.nextPosition(0, 3, allowOff = false))
        assertEquals(0, TrackCycler.nextPosition(2, 3, allowOff = false))
        assertEquals(0, TrackCycler.nextPosition(-1, 3, allowOff = false))
    }

    @Test fun subtitlesPassThroughOff() {
        assertEquals(-1, TrackCycler.nextPosition(1, 2, allowOff = true))
        assertEquals(0, TrackCycler.nextPosition(-1, 2, allowOff = true))
        assertEquals(-1, TrackCycler.nextPosition(-1, 0, allowOff = true))
    }
}
