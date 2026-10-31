package tv.jetflix

import androidx.media3.common.C
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
    @Test fun markSelectedMovesCheckToChosenSubtitle() {
        val opts = listOf(
            TrackCycler.Option(C.TRACK_TYPE_AUDIO, "English · AC3", true, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "Off", true, null, -1),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "English (SRT)", false, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "French (SRT)", false, null, 1),
            TrackCycler.Option(TrackCycler.TYPE_SPEED, "1.0x (normal)", true, null, 100),
        )
        // OK on "French (SRT)": check moves there immediately; Off and the other subtitle clear;
        // audio and speed rows are untouched.
        val out = TrackCycler.markSelected(opts, opts[3])
        assertEquals(listOf(true, false, false, true, true), out.map { it.selected })
    }

    @Test fun markSelectedOffClearsEverySubtitle() {
        val opts = listOf(
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "Off", false, null, -1),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "English (SRT)", true, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "French (SRT)", false, null, 1),
        )
        val out = TrackCycler.markSelected(opts, opts[0])
        assertEquals(listOf(true, false, false), out.map { it.selected })
    }
}
