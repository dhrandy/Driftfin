package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.details.playLabel

/**
 * Randy on build 39: "Don't need the episode named twice where the play is and right above it." When the
 * "S2:E1 · Aired ..." line above the button already names the episode, the button is a plain Play/Resume;
 * when that line is absent the button keeps the episode label so the target is still identified.
 */
class PlayLabelTest {
    private fun ep(resume: Boolean = false, withNumbers: Boolean = true) = BaseItemDto(
        Id = "e1", Type = "Episode", Name = "The Episode",
        ParentIndexNumber = if (withNumbers) 2 else null, IndexNumber = if (withNumbers) 5 else null,
        UserData = if (resume) UserItemDataDto(PlaybackPositionTicks = 1000, Played = false) else null,
    )

    @Test fun episodeNamedAboveGetsAPlainVerb() {
        assertEquals("Play", playLabel(ep(), episodeNamedAbove = true))
        assertEquals("Resume", playLabel(ep(resume = true), episodeNamedAbove = true))
    }

    @Test fun episodeNotNamedAboveKeepsItsLabel() {
        assertEquals("Play S2:E5", playLabel(ep()))
        assertEquals("Resume S2:E5", playLabel(ep(resume = true)))
    }

    @Test fun episodeWithoutNumbersAndMoviesGetAPlainVerb() {
        assertEquals("Play", playLabel(ep(withNumbers = false)))
        assertEquals("Play", playLabel(BaseItemDto(Id = "m", Type = "Movie", Name = "Film")))
        assertEquals("Resume", playLabel(BaseItemDto(Id = "m", Type = "Movie", Name = "Film", UserData = UserItemDataDto(PlaybackPositionTicks = 5, Played = false))))
    }
}
