package tv.jetflix

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.TvmazeCastResolver
import tv.jetflix.app.data.TvmazeEpisodeRef
import tv.jetflix.app.data.loadPauseCast

class TvmazePauseCastTest {
    private lateinit var jellyfin: MockWebServer
    private lateinit var tvmaze: MockWebServer
    private val repo = JellyfinRepository()

    @Before fun setUp() {
        jellyfin = MockWebServer().apply { start() }
        tvmaze = MockWebServer().apply { start() }
    }
    @After fun tearDown() { jellyfin.shutdown(); tvmaze.shutdown() }

    private fun jellyfinApi() = repo.apiFor(jellyfin.url("/").toString().trimEnd('/'))
    private fun resolver() = TvmazeCastResolver.resolver(apiOverride = TvmazeCastResolver.apiFor(tvmaze.url("/").toString()))

    private fun episode(id: String = "ep2") = BaseItemDto(
        Id = id, Type = "Episode", SeriesId = "ser", SeriesName = "Jury Duty",
        SeasonId = "sea2", ParentIndexNumber = 2, IndexNumber = 2, Name = "Team Building",
        PremiereDate = "2026-03-20T00:00:00.0000000Z",
    )

    private fun jellyfinSeason() = """{"Items":[
        {"Id":"e1","Type":"Episode","IndexNumber":1,"Name":"Onboarding","PremiereDate":"2026-03-20T00:00:00.0000000Z"},
        {"Id":"e2","Type":"Episode","IndexNumber":2,"Name":"Team Building","PremiereDate":"2026-03-20T00:00:00.0000000Z"}
    ]}""".replace("\n","")

    // A real season shape: the 8 real episodes plus 10 "Behind the Scenes" extras (E9-E18).
    private fun jellyfinSeasonWithExtras() = """{"Items":[
        {"Id":"e1","Type":"Episode","IndexNumber":1,"Name":"Onboarding","PremiereDate":"2026-03-20T00:00:00.0000000Z"},
        {"Id":"e2","Type":"Episode","IndexNumber":2,"Name":"Team Building","PremiereDate":"2026-03-20T00:00:00.0000000Z"},
        {"Id":"e9","Type":"Episode","IndexNumber":9,"Name":"Behind the Scenes of Company Retreat","PremiereDate":"2026-04-09T00:00:00.0000000Z"},
        {"Id":"e10","Type":"Episode","IndexNumber":10,"Name":"Behind the Scenes: The Cast","PremiereDate":"2026-04-09T00:00:00.0000000Z"}
    ]}""".replace("\n","")

    private fun tvmazeSearch() = """[{"show":{"id":100,"name":"Jury Duty"}},{"show":{"id":200,"name":"Jury Duty Presents: Company Retreat"}}]"""
    private fun tvmazeEpisodes(d1: String, d2: String, season: Int = 2) = """[
        {"id":501,"name":"Onboarding","season":$season,"number":1,"airdate":"$d1"},
        {"id":502,"name":"Team Building","season":$season,"number":2,"airdate":"$d2"}
    ]""".replace("\n","")
    private fun tvmazeGuestCast() = """[
        {"person":{"id":1,"name":"Tester Actor","image":{"medium":"https://img/a.jpg"}},"character":{"name":"Self"}},
        {"person":{"id":1,"name":"Tester Actor"},"character":{"name":"Self"}},
        {"person":{"id":2,"name":"Demo Guest","image":{"medium":"https://img/b.jpg"}},"character":{"name":"Christine"}}
    ]""".replace("\n","")

    private fun enqueueVerifiedSeasonThenGuestCast() {
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-21"))) // candidate 100: ep2 date mismatch -> rejected
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20"))) // candidate 200: strict match
        tvmaze.enqueue(MockResponse().setBody(tvmazeGuestCast()))
    }

    @Test fun noLocalCreditsVerifiedSeasonYieldsExactEpisodeGuestCast() = runTest {
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}""")) // episode: no actors, no guests
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeasonWithExtras())) // extras do not break the match
        enqueueVerifiedSeasonThenGuestCast()
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertEquals(listOf("Tester Actor", "Demo Guest"), cast.map { it.Name })
        // guest cast deduped by TVMaze person id (Tester Actor listed twice in the payload, shown once)
        assertEquals(2, cast.size)
        // TVMaze-only people carry no Jellyfin id (OK opens no actor page) and a full TVMaze image URL
        assertTrue(cast.all { it.Id == null })
        assertEquals("https://img/a.jpg", cast[0].RemoteImageUrl)
        // the series item is never fetched: no off-episode substitution
        assertEquals(2, jellyfin.requestCount)
    }

    @Test fun splitOffShowNumberedFromSeason1StillResolves() = runTest {
        // Provider-split season shape: the server's season 2 exists at the provider as a separate
        // show whose episodes are numbered from SEASON 1. The season label must not block the
        // fingerprint match (number + title + air date, 1:1).
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20", "2026-03-21"))) // candidate 100: date mismatch
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20", "2026-03-20", season = 1))) // candidate 200 S1: strict match
        tvmaze.enqueue(MockResponse().setBody(tvmazeGuestCast()))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertEquals(listOf("Tester Actor", "Demo Guest"), cast.map { it.Name })
    }

    @Test fun twoSeasonsOfOneCandidateBothMatchingIsAmbiguousStaysHidden() = runTest {
        // A candidate whose episode list contains TWO seasons that both fingerprint-match the
        // server season: ambiguous, no enrichment, no guestcast call.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody("""[
            {"id":501,"name":"Onboarding","season":1,"number":1,"airdate":"2026-03-20"},
            {"id":502,"name":"Team Building","season":1,"number":2,"airdate":"2026-03-20"},
            {"id":503,"name":"Onboarding","season":2,"number":1,"airdate":"2026-03-20"},
            {"id":504,"name":"Team Building","season":2,"number":2,"airdate":"2026-03-20"}
        ]""".replace("\n","")))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertTrue(cast.isEmpty())
        assertEquals(2, tvmaze.requestCount) // search + one episode list, zero guestcast calls
    }

    @Test fun localGuestStarsAreKeptAndEnrichedByVerifiedSeason() = runTest {
        // Server has only the episode's GuestStars (a real season's guest-star-only shape), and the
        // local person's provider ids verify she is TVMaze person 2 - so the TVMaze copy is dropped.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Demo Guest","Id":"g1","Type":"GuestStar","ProviderIds":{"Tvmaze":"2"}}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        enqueueVerifiedSeasonThenGuestCast()
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        // local credit kept first with its Jellyfin id; TVMaze adds the missing guest; no duplicate Demo Guest
        assertEquals(listOf("Demo Guest", "Tester Actor"), cast.map { it.Name })
        assertEquals("g1", cast[0].Id)
        assertEquals(null, cast[1].Id)
    }

    @Test fun sameEpisodeSameNameSameCharacterDropsTvmazeCopyKeepsLocal() = runTest {
        // Local GuestStar "Demo Guest" as "Christine" (no provider ids) and the TVMaze guest cast
        // lists the same name AND character for this verified episode: the TVMaze copy is hidden,
        // the local actionable card (Jellyfin id) stays, and the missing guest is still added.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Demo Guest","Id":"g1","Type":"GuestStar","Role":"Christine"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        enqueueVerifiedSeasonThenGuestCast()
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertEquals(listOf("Demo Guest", "Tester Actor"), cast.map { it.Name })
        assertEquals("g1", cast[0].Id)
        assertEquals(null, cast[1].Id)
    }

    @Test fun sameNameButDifferentCharacterStaysDistinct() = runTest {
        // Same name but a different character: ambiguous, never silently collapsed.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Demo Guest","Id":"g1","Type":"GuestStar","Role":"Someone Else"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        enqueueVerifiedSeasonThenGuestCast()
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertEquals(listOf("Demo Guest", "Tester Actor", "Demo Guest"), cast.map { it.Name })
        assertEquals("g1", cast[0].Id)
        assertEquals(null, cast[2].Id)
    }

    @Test fun unverifiableNameLookalikeIsKeptTwiceNotCollapsed() = runTest {
        // Same display name but NO provider-id link: name alone is not identity proof, so the
        // local credit and the TVMaze credit are both kept rather than silently merged.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Demo Guest","Id":"g1","Type":"GuestStar"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        enqueueVerifiedSeasonThenGuestCast()
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertEquals(listOf("Demo Guest", "Tester Actor", "Demo Guest"), cast.map { it.Name })
        assertEquals("g1", cast[0].Id)
        assertEquals(null, cast[2].Id)
    }

    @Test fun localActorAndGuestStarBothShownWithoutTvmaze() = runTest {
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Ep Actor","Id":"a1","Type":"Actor"},{"Name":"Ep Guest","Id":"g1","Type":"GuestStar"}]}"""))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), null) // feature off
        assertEquals(listOf("Ep Actor", "Ep Guest"), cast.map { it.Name })
        assertEquals(1, jellyfin.requestCount) // no season fetch, no series fetch
    }

    @Test fun noLocalCreditsAndNoVerifiedSeasonStaysEmptyNeverSeriesCast() = runTest {
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2020-01-01","2020-01-02")))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2020-01-01","2020-01-02")))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        // empty row, and the series item is NEVER fetched (no wrong-season actors on screen)
        assertTrue(cast.isEmpty())
        assertEquals(2, jellyfin.requestCount)
        jellyfin.takeRequest(); val second = jellyfin.takeRequest()
        assertFalse(second.path!!.contains("Items/ser"))
    }

    @Test fun extraEpisodeTargetGetsNoTvmazeCredits() = runTest {
        // Playing E9 (a Behind the Scenes extra): season verifies for E1-E8, but the extra has
        // no TVMaze counterpart, so NO guestcast call is made and the row falls back to local only.
        val extra = episode(id = "e9").copy(IndexNumber = 9, Name = "Behind the Scenes of Company Retreat", PremiereDate = "2026-04-09T00:00:00.0000000Z")
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeasonWithExtras()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-21")))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20")))
        // NOTE: no guestcast response enqueued - the resolver must never ask for E9's credits
        val cast = loadPauseCast(jellyfinApi(), "u", extra, resolver())
        assertTrue(cast.isEmpty()) // no local people, no off-episode substitution
        assertEquals(3, tvmaze.requestCount) // search + 2 episode lists, zero guestcast calls
    }

    @Test fun middleDisagreementFailsTheWholeSeason() = runTest {
        // Server E2 has a different title than every candidate: no verified season, empty row.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody("""{"Items":[
            {"Id":"e1","Type":"Episode","IndexNumber":1,"Name":"Onboarding","PremiereDate":"2026-03-20T00:00:00.0000000Z"},
            {"Id":"e2","Type":"Episode","IndexNumber":2,"Name":"Totally Different","PremiereDate":"2026-03-20T00:00:00.0000000Z"}
        ]}""".replace("\n","")))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20")))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20")))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertTrue(cast.isEmpty())
        assertEquals(3, tvmaze.requestCount) // no guestcast call
    }

    @Test fun ambiguousCandidatesBothMatchingStayHidden() = runTest {
        // Two candidate shows whose season 2 both fingerprint-match: ambiguous, no enrichment.
        jellyfin.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew Only","Id":"w1","Type":"Writer"}]}"""))
        jellyfin.enqueue(MockResponse().setBody(jellyfinSeason()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeSearch()))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20")))
        tvmaze.enqueue(MockResponse().setBody(tvmazeEpisodes("2026-03-20","2026-03-20")))
        val cast = loadPauseCast(jellyfinApi(), "u", episode(), resolver())
        assertTrue(cast.isEmpty())
        assertEquals(3, tvmaze.requestCount) // no guestcast call
    }

    @Test fun duplicateServerEpisodeIsAmbiguousAndFails() {
        val jf = listOf(
            BaseItemDto(IndexNumber = 1, Name = "Onboarding", PremiereDate = "2026-03-20T00:00:00Z"),
            BaseItemDto(IndexNumber = 1, Name = "Onboarding", PremiereDate = "2026-03-20T00:00:00Z"), // duplicate row
        )
        val tm = listOf(TvmazeEpisodeRef(id = 501, name = "Onboarding", season = 2, number = 1, airdate = "2026-03-20"))
        assertFalse(TvmazeCastResolver.fingerprintMatches(jf, tm))
    }

    @Test fun fingerprintToleratesServerExtrasButNotFewer() {
        val jf = listOf(
            BaseItemDto(IndexNumber = 1, Name = "Onboarding", PremiereDate = "2026-03-20T00:00:00Z"),
            BaseItemDto(IndexNumber = 2, Name = "Team Building", PremiereDate = "2026-03-20T00:00:00Z"),
            BaseItemDto(IndexNumber = 9, Name = "Behind the Scenes", PremiereDate = "2026-04-09T00:00:00Z"), // extra
        )
        val tm = listOf(
            TvmazeEpisodeRef(id = 501, name = "Onboarding", season = 2, number = 1, airdate = "2026-03-20"),
            TvmazeEpisodeRef(id = 502, name = "Team Building", season = 2, number = 2, airdate = "2026-03-20"),
        )
        assertTrue(TvmazeCastResolver.fingerprintMatches(jf, tm)) // extra server rows are fine
        assertFalse(TvmazeCastResolver.fingerprintMatches(jf.take(1), tm)) // but server may not be missing one
    }

    @Test fun fingerprintRejectsTitleOnlyCoincidence() {
        val jf = listOf(
            BaseItemDto(IndexNumber = 1, Name = "Pilot", PremiereDate = null),
            BaseItemDto(IndexNumber = 2, Name = "Finale", PremiereDate = null),
        )
        val tm = listOf(
            TvmazeEpisodeRef(id = 501, name = "Pilot", season = 2, number = 1, airdate = null),
            TvmazeEpisodeRef(id = 502, name = "Finale", season = 2, number = 2, airdate = null),
        )
        assertFalse(TvmazeCastResolver.fingerprintMatches(jf, tm)) // no dates on either side
    }

    @Test fun fingerprintRejectsOneMissingServerDate() {
        val jf = listOf(
            BaseItemDto(IndexNumber = 1, Name = "A", PremiereDate = "2026-01-01T00:00:00Z"),
            BaseItemDto(IndexNumber = 2, Name = "B", PremiereDate = null), // server date missing
        )
        val tm = listOf(
            TvmazeEpisodeRef(id = 501, name = "A", season = 2, number = 1, airdate = "2026-01-01"),
            TvmazeEpisodeRef(id = 502, name = "B", season = 2, number = 2, airdate = "2026-01-08"),
        )
        assertFalse(TvmazeCastResolver.fingerprintMatches(jf, tm))
    }

    @Test fun fingerprintAcceptsExactTitlesAndDates() {
        val jf = listOf(
            BaseItemDto(IndexNumber = 1, Name = "Mergers & Acquisitions", PremiereDate = "2026-04-03T00:00:00Z"),
            BaseItemDto(IndexNumber = 2, Name = "Employee Review", PremiereDate = "2026-04-03T00:00:00Z"),
        )
        val tm = listOf(
            TvmazeEpisodeRef(id = 501, name = "mergers and acquisitions!", season = 2, number = 1, airdate = "2026-04-03"),
            TvmazeEpisodeRef(id = 502, name = "Employee Review", season = 2, number = 2, airdate = "2026-04-03"),
        )
        assertTrue(TvmazeCastResolver.fingerprintMatches(jf, tm))
    }

    @Test fun fingerprintRejectsCountMismatch() {
        val jf = listOf(BaseItemDto(IndexNumber = 1, Name = "A", PremiereDate = "2026-01-01T00:00:00Z"))
        val tm = listOf(
            TvmazeEpisodeRef(id = 501, name = "A", season = 2, number = 1, airdate = "2026-01-01"),
            TvmazeEpisodeRef(id = 502, name = "B", season = 2, number = 2, airdate = "2026-01-08"),
        )
        assertFalse(TvmazeCastResolver.fingerprintMatches(jf, tm))
    }
}
