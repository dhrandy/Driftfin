package tv.jetflix.phone

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.BaseItemPerson
import tv.jetflix.phone.data.TvmazeCastResolver
import tv.jetflix.phone.data.detailsCast

/**
 * The phone details cast row (user report, phone build 8: a Jury Duty season 2 episode showed
 * season 1's actors from the series cast fallback). An episode shows its own credits plus the
 * verified TVMaze season's guest cast - verified by episode fingerprint, never by name or season
 * number - and NEVER the series cast.
 */
class PhoneTvmazeCastTest {
    private lateinit var tvmaze: MockWebServer

    @Before fun setUp() { tvmaze = MockWebServer().apply { start() } }
    @After fun tearDown() { tvmaze.shutdown() }

    private fun resolver() = TvmazeCastResolver(TvmazeCastResolver.apiFor(tvmaze.url("/").toString()))

    private fun episode() = BaseItemDto(
        Id = "ep2", Type = "Episode", SeriesId = "ser", SeriesName = "Jury Duty",
        SeasonId = "sea2", ParentIndexNumber = 2, IndexNumber = 2, Name = "Team Building",
        PremiereDate = "2026-03-27T00:00:00.0000000Z",
    )

    // The server's season 2: 8 real episodes + 2 "Behind the Scenes" extras numbered past the season.
    private fun season(withExtras: Boolean = false) = buildList {
        add(BaseItemDto(Id = "e1", Type = "Episode", IndexNumber = 1, Name = "Onboarding", PremiereDate = "2026-03-20T00:00:00.0000000Z"))
        add(BaseItemDto(Id = "e2", Type = "Episode", IndexNumber = 2, Name = "Team Building", PremiereDate = "2026-03-27T00:00:00.0000000Z"))
        if (withExtras) {
            add(BaseItemDto(Id = "e9", Type = "Episode", IndexNumber = 9, Name = "Behind the Scenes of Company Retreat", PremiereDate = "2026-04-09T00:00:00.0000000Z"))
        }
    }

    private fun searchBody() = """[{"show":{"id":100,"name":"Jury Duty"}},{"show":{"id":200,"name":"Jury Duty Presents: Company Retreat"}}]"""
    // Show 100's season 2 does NOT match (wrong air dates); show 200's season 1 matches exactly.
    private fun wrongSeasonBody() = """[
        {"id":501,"name":"Onboarding","season":2,"number":1,"airdate":"2025-04-18"},
        {"id":502,"name":"Team Building","season":2,"number":2,"airdate":"2025-04-18"}
    ]"""
    private fun splitSeasonBody() = """[
        {"id":601,"name":"Onboarding","season":1,"number":1,"airdate":"2026-03-20"},
        {"id":602,"name":"Team Building","season":1,"number":2,"airdate":"2026-03-27"}
    ]"""
    private fun guestCastBody() = """[
        {"person":{"id":187924,"name":"Lisa Gilroy","image":{"medium":"https://img/lisa.jpg"}},"character":{"name":"Christine"}},
        {"person":{"id":145212,"name":"Nick Rutherford"},"character":{"name":""}}
    ]"""

    private fun enqueueVerifiedSplitSeason() {
        tvmaze.enqueue(MockResponse().setBody(searchBody()))
        tvmaze.enqueue(MockResponse().setBody(wrongSeasonBody())) // show 100 episodes
        tvmaze.enqueue(MockResponse().setBody(splitSeasonBody())) // show 200 episodes: season 1 matches
        tvmaze.enqueue(MockResponse().setBody(guestCastBody()))   // guestcast for episode 602
    }

    @Test fun verifiedSplitSeasonSuppliesTheSeasonsGuests() = runTest {
        enqueueVerifiedSplitSeason()
        val cast = detailsCast(episode(), season(), resolver())
        assertEquals(listOf("Lisa Gilroy", "Nick Rutherford"), cast.map { it.Name })
        // Display-only: no Jellyfin ids, TVMaze identity preserved, remote photo carried.
        assertEquals(null, cast[0].Id)
        assertEquals("187924", cast[0].ProviderIds?.get("Tvmaze"))
        assertEquals("https://img/lisa.jpg", cast[0].RemoteImageUrl)
        assertEquals("Christine", cast[0].Role)
    }

    @Test fun extrasOnTheServerDoNotBreakVerification() = runTest {
        enqueueVerifiedSplitSeason()
        val cast = detailsCast(episode(), season(withExtras = true), resolver())
        assertEquals(listOf("Lisa Gilroy", "Nick Rutherford"), cast.map { it.Name })
    }

    @Test fun unverifiedSeasonShowsOwnCreditsOnlyNeverTheSeriesCast() = runTest {
        // Both candidate shows mismatch (wrong dates everywhere): no verification, no guests,
        // and the episode's empty own credits stay empty - the series cast is never substituted.
        tvmaze.enqueue(MockResponse().setBody(searchBody()))
        tvmaze.enqueue(MockResponse().setBody(wrongSeasonBody()))
        tvmaze.enqueue(MockResponse().setBody(wrongSeasonBody()))
        val cast = detailsCast(episode(), season(), resolver())
        assertTrue(cast.isEmpty())
    }

    @Test fun twoMatchingCandidateSeasonsAreAmbiguousAndStayHidden() = runTest {
        tvmaze.enqueue(MockResponse().setBody(searchBody()))
        tvmaze.enqueue(MockResponse().setBody(splitSeasonBody())) // show 100 also matches
        tvmaze.enqueue(MockResponse().setBody(splitSeasonBody())) // show 200 matches too
        val cast = detailsCast(episode(), season(), resolver())
        assertTrue(cast.isEmpty())
    }

    @Test fun ownCreditsStayFirstAndGuestsMergeIn() = runTest {
        enqueueVerifiedSplitSeason()
        val own = BaseItemPerson(Id = "jp1", Name = "Server Actor", Role = "Lead", Type = "Actor",
            ProviderIds = mapOf("Tvmaze" to "187924"))
        val cast = detailsCast(episode().copy(People = listOf(own)), season(), resolver())
        // Local first with its Jellyfin id; the local person's TVMaze id proves Lisa Gilroy is the
        // same person, so her duplicate guest card is dropped; Nick Rutherford is added.
        assertEquals(listOf("Server Actor", "Nick Rutherford"), cast.map { it.Name })
        assertEquals("jp1", cast[0].Id)
    }

    @Test fun sameNameAndCharacterDeduplicatesButNameAloneKeepsBoth() = runTest {
        enqueueVerifiedSplitSeason()
        // Same name AND same character as the verified guest: the local card wins, guest hidden.
        val dup = BaseItemPerson(Id = "jp2", Name = "lisa gilroy", Role = "Christine", Type = "Actor")
        // Same name, different character: ambiguous, both stay.
        val lookalike = BaseItemPerson(Id = "jp3", Name = "Nick Rutherford", Role = "Someone Else", Type = "Actor")
        val cast = detailsCast(episode().copy(People = listOf(dup, lookalike)), season(), resolver())
        assertEquals(listOf("lisa gilroy", "Nick Rutherford", "Nick Rutherford"), cast.map { it.Name })
        assertEquals(listOf("jp2", "jp3", null), cast.map { it.Id })
    }

    @Test fun nonEpisodeItemsUseTheirOwnCreditsUnchanged() = runTest {
        val movie = BaseItemDto(Id = "m1", Type = "Movie", Name = "Film",
            People = listOf(BaseItemPerson(Id = "a1", Name = "Movie Star", Type = "Actor")))
        val cast = detailsCast(movie, emptyList(), resolver())
        assertEquals(listOf("Movie Star"), cast.map { it.Name })
        assertEquals(0, tvmaze.requestCount) // no network for non-episodes
    }

    @Test fun idlessCreditsNeverMaterializeABogusPersonOrCollapseTogether() = runTest {
        // Two credits with no usable person id (model default 0) must both be dropped: no
        // "tvmaze-0" identity, and they must not deduplicate into one false person.
        tvmaze.enqueue(MockResponse().setBody(searchBody()))
        tvmaze.enqueue(MockResponse().setBody(wrongSeasonBody()))
        tvmaze.enqueue(MockResponse().setBody(splitSeasonBody()))
        tvmaze.enqueue(MockResponse().setBody("""[
            {"person":{"name":"No Id One"},"character":{"name":"A"}},
            {"person":{"id":0,"name":"No Id Two"},"character":{"name":"B"}},
            {"person":{"id":145212,"name":"Nick Rutherford"},"character":{"name":""}}
        ]"""))
        val cast = detailsCast(episode(), season(), resolver())
        assertEquals(listOf("Nick Rutherford"), cast.map { it.Name })
        assertEquals("145212", cast[0].ProviderIds?.get("Tvmaze"))
    }

    @Test fun settingsToggleOffDisablesResolution() {
        tv.jetflix.phone.data.PhoneSettings.setTvmazeEpisodeCast(false)
        try {
            assertNull(TvmazeCastResolver.resolver())
        } finally {
            tv.jetflix.phone.data.PhoneSettings.setTvmazeEpisodeCast(true)
            TvmazeCastResolver.resetForTests()
        }
    }

    @Test fun cancellingALookupLeavesNothingCached() = kotlinx.coroutines.runBlocking {
        val r = resolver()
        // The search hangs; leaving the page cancels the lookup mid-flight.
        tvmaze.enqueue(MockResponse().setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS).setBody(searchBody()))
        var cancelled = false
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            try { detailsCast(episode(), season(), r) } catch (ce: kotlinx.coroutines.CancellationException) { cancelled = true }
        }
        kotlinx.coroutines.delay(500)
        job.cancel()
        job.join()
        org.junit.Assert.assertTrue("the in-flight lookup observed its cancellation", cancelled)
        // Nothing was cached by the cancelled lookup: the next open verifies and resolves.
        enqueueVerifiedSplitSeason()
        val cast = detailsCast(episode(), season(), r)
        assertEquals(listOf("Lisa Gilroy", "Nick Rutherford"), cast.map { it.Name })
    }

    @Test fun episodeWithoutSeriesOrSeasonNumbersStaysLocal() = runTest {
        val orphan = episode().copy(SeriesId = null)
        assertTrue(detailsCast(orphan, season(), resolver()).isEmpty())
        assertEquals(0, tvmaze.requestCount)
    }
}
