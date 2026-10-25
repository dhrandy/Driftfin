package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BannerPicks
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.UserItemDataDto

class BannerPicksTest {
    private fun movie(id: String, rating: Float?, played: Boolean = false, pos: Long = 0, last: String? = null, type: String = "Movie") =
        BaseItemDto(Id = id, Name = id, Type = type, CommunityRating = rating, UserData = UserItemDataDto(PlaybackPositionTicks = pos, Played = played, LastPlayedDate = last))

    private val all = { _: BaseItemDto -> true }

    @Test fun homePicksOnlyUnwatchedMoviesBestRatedFirst() {
        val items = listOf(
            movie("a", 6.0f), movie("b", 8.5f), movie("c", 9.0f, played = true), movie("d", 7.5f),
            movie("e", 9.5f, pos = 1000), movie("f", 8.0f, type = "Episode"), movie("g", 9.9f, last = "2026-01-01"),
        )
        assertEquals(listOf("b", "d"), BannerPicks.topUnwatchedMovies(items, all, 6).map { it.Id })
    }

    @Test fun homeFallsBackToBestUnwatchedWhenNoneRateHighAndNeverToWatched() {
        val items = listOf(movie("a", 5.0f), movie("b", 6.5f), movie("c", 9.0f, played = true))
        assertEquals(listOf("b", "a"), BannerPicks.topUnwatchedMovies(items, all, 6).map { it.Id })
        assertTrue(BannerPicks.topUnwatchedMovies(listOf(movie("c", 9.0f, played = true)), all, 6).isEmpty())
    }

    @Test fun sectionBannerIsRandomUnwatchedOfTheRightTypeOrNothing() {
        val items = listOf(movie("m1", 5f), movie("m2", 5f, played = true), movie("s1", null, type = "Series"), movie("s2", null, type = "Series", last = "2026-02-02"))
        repeat(20) {
            assertEquals("m1", BannerPicks.randomUnwatched(items, isShows = false, hasBackdrop = all)?.Id)
            assertEquals("s1", BannerPicks.randomUnwatched(items, isShows = true, hasBackdrop = all)?.Id)
        }
        assertNull(BannerPicks.randomUnwatched(listOf(movie("m2", 5f, played = true)), isShows = false, hasBackdrop = all))
        assertNull(BannerPicks.randomUnwatched(items, isShows = true, hasBackdrop = { false }))
    }

    @Test fun screensaverUsesOnlyUnwatchedMoviesWithArtAndNeverRunsDuringPlayback() {
        val items = listOf(movie("a", 5f), movie("b", 6f, played = true), movie("c", 7f, type = "Episode"), movie("d", 7f, pos = 5), movie("e", 8f))
        assertEquals(listOf("a", "e"), BannerPicks.randomUnwatchedList(items, all).map { it.Id })
        assertTrue(BannerPicks.randomUnwatchedList(items) { false }.isEmpty())
        assertTrue(!tv.jetflix.app.ui.components.ScreensaverRules.mayRun(true, listOf(tv.jetflix.app.ui.components.ScreensaverSlide("x"))))
        assertTrue(!tv.jetflix.app.ui.components.ScreensaverRules.mayRun(false, emptyList()))
        assertTrue(tv.jetflix.app.ui.components.ScreensaverRules.mayRun(false, listOf(tv.jetflix.app.ui.components.ScreensaverSlide("x"))))
    }
}
