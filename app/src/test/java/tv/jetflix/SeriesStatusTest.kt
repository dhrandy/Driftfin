package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.ui.details.seriesStatusLabel

/** The label beside the season buttons: only the server's own status, never a guessed one. */
class SeriesStatusTest {
    @Test fun continuingReadsReturningSeries() {
        assertEquals("Returning Series", seriesStatusLabel("Continuing"))
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
