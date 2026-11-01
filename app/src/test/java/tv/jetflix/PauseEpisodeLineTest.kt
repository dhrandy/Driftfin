package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto

class PauseEpisodeLineTest {
    @Test fun episodeLineAppendsAirDateWithMiddleDot() {
        val ep = BaseItemDto(
            Type = "Episode", ParentIndexNumber = 2, IndexNumber = 2, Name = "Team Building",
            PremiereDate = "2026-03-20T00:00:00.0000000Z",
        )
        assertEquals("S2:E2 · Team Building · Mar 20, 2026", ep.pauseEpisodeLine())
    }

    @Test fun noServerDateNoExtraText() {
        val ep = BaseItemDto(Type = "Episode", ParentIndexNumber = 2, IndexNumber = 2, Name = "Team Building")
        assertEquals("S2:E2 · Team Building", ep.pauseEpisodeLine())
    }

    @Test fun lateUtcTimeDoesNotShiftTheDay() {
        // 23:30 UTC on the 20th stays the 20th: the label is the server's date part verbatim.
        val ep = BaseItemDto(Type = "Episode", ParentIndexNumber = 1, IndexNumber = 5, Name = "X", PremiereDate = "2026-03-20T23:30:00.0000000Z")
        assertEquals("S1:E5 · X · Mar 20, 2026", ep.pauseEpisodeLine())
    }

    @Test fun missingLabelPartsAreSkipped() {
        val ep = BaseItemDto(Type = "Episode", Name = "Team Building", PremiereDate = "2026-03-20T00:00:00Z")
        assertEquals("Team Building · Mar 20, 2026", ep.pauseEpisodeLine())
    }
}
