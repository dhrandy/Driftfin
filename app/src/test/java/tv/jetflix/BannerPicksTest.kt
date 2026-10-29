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

    @Test fun homeBannerIsSixRandomOfTheTopFiftyEligible() {
        val items = (1..80).map { movie("m$it", 9.9f - it * 0.05f) } + movie("w", 9.99f, played = true)
        val top50 = BannerPicks.topUnwatchedMovies(items, all, 50).map { it.Id }.toSet()
        assertEquals(50, top50.size)
        val seen = mutableSetOf<String>()
        repeat(20) { n ->
            val pick = BannerPicks.randomTopUnwatchedMovies(items, all, 6, random = kotlin.random.Random(n)).map { it.Id }
            assertEquals(6, pick.size); assertEquals(6, pick.toSet().size)
            assertTrue(top50.containsAll(pick)); assertTrue("w" !in pick)
            seen += pick
        }
        assertTrue("picks should vary between loads", seen.size > 6)
    }

    @Test fun homeBannerFallsBackToStartedButUnfinishedWhenNothingUntouchedSurvives() {
        // Todd's row-82/86 repro: the animation option plus the untouched gate empty the pool. The banner
        // retries with started-but-unfinished titles allowed; watched and animation stay excluded either way.
        val started = movie("s", 8.5f, pos = 1000)
        val watched = movie("w", 9.9f, played = true)
        val animated = movie("a", 9.5f).copy(Genres = listOf("Animation"))
        val picks = BannerPicks.randomTopUnwatchedMovies(listOf(started, watched, animated), all, 6, excludeAnimation = true)
        assertEquals(listOf("s"), picks.map { it.Id })
    }

    @Test fun homeBannerPrefersUntouchedTitlesOverStartedOnes() {
        val started = movie("s", 9.9f, pos = 1000)
        val fresh = movie("f", 7.5f)
        assertEquals(listOf("f"), BannerPicks.randomTopUnwatchedMovies(listOf(started, fresh), all, 6).map { it.Id })
    }

    @Test fun homeBannerStaysEmptyWhenEverythingIsWatched() {
        assertTrue(BannerPicks.randomTopUnwatchedMovies(listOf(movie("w", 9.9f, played = true)), all, 6).isEmpty())
    }

    @Test fun homeBannerWithFewEligibleShowsThemAll() {
        val items = listOf(movie("a", 8f), movie("b", 7.5f), movie("c", 9f, played = true))
        assertEquals(setOf("a", "b"), BannerPicks.randomTopUnwatchedMovies(items, all, 6).map { it.Id }.toSet())
    }
}
