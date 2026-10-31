package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.player.upNextLine

/** The "Up next: ..." line on the paused player overlay. */
class UpNextLineTest {
    private val next = BaseItemDto(Id = "n", Type = "Episode", Name = "Back to Town", ParentIndexNumber = 2, IndexNumber = 6)

    @Test fun numberedEpisodeGetsItsLabelAndName() =
        assertEquals("Up next: S2:E6 · Back to Town", upNextLine(next))

    @Test fun unnumberedEpisodeFallsBackToItsName() =
        assertEquals("Up next: Back to Town", upNextLine(next.copy(ParentIndexNumber = null, IndexNumber = null)))

    @Test fun noNextEpisodeShowsNothing() =
        assertNull(upNextLine(null))

    @Test fun namelessNextShowsNothing() =
        assertNull(upNextLine(next.copy(Name = null, ParentIndexNumber = null, IndexNumber = null)))
}
