package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.ui.details.seriesStatusLabel

/** The series status on the genre line: only the server's own status, never a guessed one. */
class SeriesStatusTest {
    @Test fun continuingReadsContinuing() {
        assertEquals("Continuing", seriesStatusLabel("Continuing"))
    }

    @Test fun endedReadsEnded() {
        assertEquals("Ended", seriesStatusLabel("Ended"))
        assertEquals("Ended", seriesStatusLabel("ended"))
    }

    @Test fun canceledReadsCanceled() {
        assertEquals("Canceled", seriesStatusLabel("Canceled"))
    }

    @Test fun unknownOrMissingShowsNothing() {
        assertNull(seriesStatusLabel(null))
        assertNull(seriesStatusLabel(""))
        assertNull(seriesStatusLabel("In Production"))
    }
}
