package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.details.advancedTargetAfterWatched
import tv.jetflix.app.ui.details.mergedCast
import tv.jetflix.app.ui.details.metaAiredText
import tv.jetflix.app.ui.details.watchedToggleTarget

class DetailsProgressionTest {

    private fun series(id: String = "series1", premiered: String? = null) =
        BaseItemDto(Id = id, Type = "Series", PremiereDate = premiered)

    private fun movie(id: String = "movie1") = BaseItemDto(Id = id, Type = "Movie")

    private fun ep(
        id: String,
        season: Int = 1,
        n: Int = 1,
        played: Boolean = false,
        aired: String? = null,
        runtimeTicks: Long? = null,
    ) = BaseItemDto(
        Id = id, Type = "Episode", SeasonId = "s$season", ParentIndexNumber = season,
        IndexNumber = n, PremiereDate = aired, RunTimeTicks = runtimeTicks,
        UserData = UserItemDataDto(Played = played),
    )

    private fun actor(id: String, name: String) = BaseItemPerson(Name = name, Id = id, Type = "Actor")

    // --- watched scope: one episode, never the whole series ---

    @Test fun seriesPageCheckTargetsThePlayTargetEpisodeNeverTheSeries() {
        val target = watchedToggleTarget(series("series1"), ep("e2", n = 2))
        assertEquals("e2", target.Id)
        assertEquals("Episode", target.Type)
    }

    @Test fun moviePageCheckTargetsTheMovieItself() {
        val target = watchedToggleTarget(movie("movie1"), movie("movie1"))
        assertEquals("movie1", target.Id)
    }

    @Test fun seriesPageWithoutEpisodeTargetFallsBackToPageItem() {
        assertEquals("series1", watchedToggleTarget(series("series1"), null).Id)
        assertEquals("series1", watchedToggleTarget(series("series1"), movie("weird")).Id)
    }

    // --- mark-watched advances the page target ---

    private val season = listOf(
        ep("e1", n = 1, played = true), ep("e2", n = 2), ep("e3", n = 3),
    )

    @Test fun markingWatchedAdvancesToNextEpisodeInServerOrder() {
        val marked = season.map { if (it.Id == "e2") it.copy(UserData = UserItemDataDto(Played = true)) else it }
        assertEquals("e3", advancedTargetAfterWatched(marked, "e2", played = true, current = marked[1])!!.Id)
    }

    @Test fun markingLastEpisodeWatchedFallsBackToGeneralPick() {
        val marked = season.map { it.copy(UserData = UserItemDataDto(Played = true)) }
        // Next after e3 doesn't exist; the general pick keeps a sane target rather than going blank.
        assertEquals("e1", advancedTargetAfterWatched(marked, "e3", played = true, current = marked[2])!!.Id)
    }

    @Test fun unmarkingReturnsTargetToTheClearedEpisode() {
        val unmarked = season.map { if (it.Id == "e1") it.copy(UserData = UserItemDataDto(Played = false)) else it }
        assertEquals("e1", advancedTargetAfterWatched(unmarked, "e1", played = false, current = unmarked[1])!!.Id)
    }

    // --- runtime beside the air date ---

    @Test fun seriesMetaShowsEpisodeAirDateWithRuntime() {
        val target = ep("e2", n = 2, aired = "2026-03-08T00:00:00Z", runtimeTicks = 42L * 600_000_000)
        assertEquals("Aired Mar 8, 2026 · 42m", metaAiredText(series(), target))
    }

    @Test fun runtimeOmittedWhenEpisodeHasNone() {
        val target = ep("e2", n = 2, aired = "2026-03-08T00:00:00Z")
        assertEquals("Aired Mar 8, 2026", metaAiredText(series(), target))
    }

    @Test fun premiereDateHoldsSlotWhenEpisodeHasNoDate() {
        val target = ep("e2", n = 2, runtimeTicks = 42L * 600_000_000)
        assertEquals("Premiered Sep 17, 2025", metaAiredText(series(premiered = "2025-09-17T00:00:00Z"), target))
    }

    @Test fun movieMetaUsesItsOwnPathNotTheEpisodeSlot() {
        assertNull(metaAiredText(movie(), null))
    }

    // --- cast: episode guest cast fills sparse series metadata ---

    @Test fun episodeGuestCastFillsSparseSeriesCast() {
        val seriesCast = listOf(actor("p1", "Lead"))
        val episodeCast = listOf(actor("p1", "Lead"), actor("p2", "Guest One"), actor("p3", "Guest Two"))
        val merged = mergedCast(seriesCast, episodeCast)
        assertEquals(listOf("p1", "p2", "p3"), merged.map { it.Id })
    }

    @Test fun seriesCastStaysFirstAndDedupesByNameWhenIdsMissing() {
        val seriesCast = listOf(actor(null.toString(), "Lead").copy(Id = null))
        val episodeCast = listOf(BaseItemPerson(Name = "Lead", Type = "Actor"), actor("p9", "Guest"))
        val merged = mergedCast(seriesCast, episodeCast)
        assertEquals(listOf("Lead", "Guest"), merged.map { it.Name })
    }

    @Test fun castCapStaysAtFifteen() {
        val many = (1..12).map { actor("s$it", "Series $it") }
        val guests = (1..8).map { actor("g$it", "Guest $it") }
        val merged = mergedCast(many, guests)
        assertEquals(15, merged.size)
        assertEquals("s1", merged.first().Id)
        assertEquals("g3", merged.last().Id)
    }

    @Test fun noPeopleAtAllGivesEmptyCast() {
        assertTrue(mergedCast(null, null).isEmpty())
    }
}
