package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.EpisodeTarget
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.data.fromStart

class EpisodeTargetTest {
    private fun ep(id: String, season: Int, n: Int, played: Boolean = false, pos: Long = 0, last: String? = null) =
        BaseItemDto(
            Id = id, Type = "Episode", SeasonId = "s$season", ParentIndexNumber = season, IndexNumber = n,
            UserData = UserItemDataDto(PlaybackPositionTicks = pos, Played = played, LastPlayedDate = last),
        )

    private val all = listOf(
        ep("a", 1, 1, played = true, last = "2026-01-01"), ep("b", 1, 2, played = true, last = "2026-01-02"),
        ep("c", 2, 1, played = true, last = "2026-01-03"), ep("d", 2, 2), ep("e", 2, 3), ep("f", 3, 1),
    )

    @Test fun inProgressEpisodeInLaterSeasonWinsOverSeasonOne() {
        val eps = all.map { if (it.Id == "e") it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 9000, LastPlayedDate = "2026-02-01")) else it }
        assertEquals("e", EpisodeTarget.pick(eps)!!.Id)
        assertEquals("s2", EpisodeTarget.pick(eps)!!.SeasonId)
    }

    @Test fun mostRecentlyStartedWinsWhenSeveralAreInProgress() {
        val eps = all.map {
            when (it.Id) {
                "d" -> it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 5, LastPlayedDate = "2026-03-01"))
                "f" -> it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 5, LastPlayedDate = "2026-02-01"))
                else -> it
            }
        }
        assertEquals("d", EpisodeTarget.pick(eps)!!.Id)
    }

    @Test fun nothingInProgressContinuesAfterLastWatchedNotFirstUnwatched() {
        // Season 1 episode 2 is unwatched-gap free here; make a gap: user skipped ahead and watched c last.
        val eps = all.map { if (it.Id == "b") it.copy(UserData = UserItemDataDto()) else it }
        assertEquals("d", EpisodeTarget.pick(eps)!!.Id)
    }

    @Test fun lastWatchedWasFinalEpisodeFallsBackToFirstUnwatchedThenFirst() {
        val finished = all.map { it.copy(UserData = UserItemDataDto(Played = true, LastPlayedDate = "2026-0${all.indexOf(it) + 1}")) }
        assertEquals("a", EpisodeTarget.pick(finished)!!.Id)
        assertTrue(EpisodeTarget.pick(emptyList()) == null)
    }

    @Test fun neverStartedPlaysEpisodeOne() {
        assertEquals("a", EpisodeTarget.pick(all.map { it.copy(UserData = UserItemDataDto()) })!!.Id)
    }

    @Test fun playFromStartZeroesTheSavedPositionAndKeepsTheItem() {
        val e = ep("x", 1, 1, pos = 123456, last = "z")
        val f = e.fromStart()
        assertEquals(0L, f.UserData!!.PlaybackPositionTicks)
        assertEquals("x", f.Id)
        assertEquals("z", f.UserData!!.LastPlayedDate)
        assertTrue(e.isResumeable && !f.isResumeable)
    }
}
