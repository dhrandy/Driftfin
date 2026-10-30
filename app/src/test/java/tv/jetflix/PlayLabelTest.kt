package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.details.playLabel

/**
 * The button names the episode it will play ("Resume S2:E5"), and with the header's air-date line gone
 * (build 41: the date moved to the genre line) nothing up top names it twice. Movies and episodes without
 * numbers get a plain Play/Resume.
 */
class PlayLabelTest {
    private fun ep(resume: Boolean = false, withNumbers: Boolean = true) = BaseItemDto(
        Id = "e1", Type = "Episode", Name = "The Episode",
        ParentIndexNumber = if (withNumbers) 2 else null, IndexNumber = if (withNumbers) 5 else null,
        UserData = if (resume) UserItemDataDto(PlaybackPositionTicks = 1000, Played = false) else null,
    )

    @Test fun episodesKeepTheirLabel() {
        assertEquals("Play S2:E5", playLabel(ep()))
        assertEquals("Resume S2:E5", playLabel(ep(resume = true)))
    }

    @Test fun episodeWithoutNumbersAndMoviesGetAPlainVerb() {
        assertEquals("Play", playLabel(ep(withNumbers = false)))
        assertEquals("Play", playLabel(BaseItemDto(Id = "m", Type = "Movie", Name = "Film")))
        assertEquals("Resume", playLabel(BaseItemDto(Id = "m", Type = "Movie", Name = "Film", UserData = UserItemDataDto(PlaybackPositionTicks = 5, Played = false))))
    }
}
