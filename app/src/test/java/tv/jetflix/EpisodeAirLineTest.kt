package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.details.episodeAirLine

/** The series page names the episode Play will start and when it aired, apart from the show's premiere date. */
class EpisodeAirLineTest {
    private val ep = BaseItemDto(Id = "e3", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 3, PremiereDate = "2023-04-21T00:00:00.0000000Z")

    @Test fun episodeWithADateGivesLabelAndAiredDate() = assertEquals("S1:E3" to "Aired Apr 21, 2023", episodeAirLine(ep))
    @Test fun noDateMeansNoLine() = assertNull(episodeAirLine(ep.copy(PremiereDate = null)))
    @Test fun noNumbersMeansNoLine() = assertNull(episodeAirLine(ep.copy(IndexNumber = null)))
    @Test fun seriesOrMovieTargetGivesNoLine() {
        assertNull(episodeAirLine(ep.copy(Type = "Series")))
        assertNull(episodeAirLine(ep.copy(Type = "Movie")))
    }
}
