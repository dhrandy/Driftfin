package tv.jetflix.phone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.JellyfinRepository
import tv.jetflix.phone.ui.UpNextLookup
import tv.jetflix.phone.ui.absoluteTicks
import tv.jetflix.phone.ui.StopReportOnce
import tv.jetflix.phone.ui.reportStopAndMarkPlayed
import tv.jetflix.phone.ui.earnsWatchedAtEnd
import tv.jetflix.phone.ui.lookupUpNext
import tv.jetflix.phone.ui.reportFinishThenNavigate
import tv.jetflix.phone.ui.reportStopThenOpenNext

/** The phone player's natural-end completion: stop report + watched mark, awaited before navigation. */
class PhoneFinishTest {
    private val server = MockWebServer()
    private val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val api by lazy { JellyfinRepository().apiFor(server.url("/").toString().trimEnd('/')) }

    private fun serve(body: String = """{"Items":[],"TotalRecordCount":0}""", code: Int = 200) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += "${request.method} ${request.path.orEmpty().substringBefore('?')}"
                return MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
            }
        }
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    /** Resumed HLS transcodes count their stream from zero at the resume offset, so every reported
     *  position needs the base added back; direct play is absolute already (offset 0). */
    @Test fun reportedPositionsCarryTheStreamBase() {
        // Direct play: offset 0, position unchanged.
        assertEquals(1_200_000L * 10_000, absoluteTicks(1_200_000L, 0))
        // HLS resumed at 20:00: 5 stream-minutes in must report 25:00, not 0:05.
        val resumeTicks = 20 * 60 * 10_000_000L
        assertEquals(resumeTicks + 5 * 60 * 10_000_000L, absoluteTicks(5 * 60 * 1_000L, resumeTicks))
        // A negative player reading clamps to the base, never below it.
        assertEquals(resumeTicks, absoluteTicks(-1, resumeTicks))
    }

    @Test fun onlyMoviesAndEpisodesEarnTheMark() {
        assertTrue(earnsWatchedAtEnd(BaseItemDto(Id = "m", Type = "Movie")))
        assertTrue(earnsWatchedAtEnd(BaseItemDto(Id = "e", Type = "Episode")))
        assertFalse(earnsWatchedAtEnd(BaseItemDto(Id = "s", Type = "Series")))
        assertFalse(earnsWatchedAtEnd(BaseItemDto(Id = "t", Type = "Trailer")))
    }

    @Test fun lookupFindsServerOrderNext() = runBlocking {
        serve("""{"Items":[{"Id":"a","Type":"Episode"},{"Id":"b","Type":"Episode"}]}""")
        val r = lookupUpNext(api, "series1", "u1", "a")
        assertTrue(r is UpNextLookup.Found && r.next.Id == "b")
    }

    @Test fun lookupReportsNoneOnLastEpisode() = runBlocking {
        serve("""{"Items":[{"Id":"a","Type":"Episode"}]}""")
        assertEquals(UpNextLookup.None, lookupUpNext(api, "series1", "u1", "a"))
    }

    @Test fun lookupReportsFailedOnServerError() = runBlocking {
        serve(code = 500)
        assertEquals(UpNextLookup.Failed, lookupUpNext(api, "series1", "u1", "a"))
    }

    @Test fun finishWaitsForStopAndMarkBeforeNavigating() = runBlocking {
        serve("""{}""")
        var navigated = false
        reportFinishThenNavigate(api, BaseItemDto(Id = "m1", Type = "Movie"), "u1", "src", "sess", 1234L) { navigated = true }
        assertTrue(navigated)
        assertEquals(listOf("POST /Sessions/Playing/Stopped", "POST /UserPlayedItems/m1"), seen)
    }

    @Test fun finishStillNavigatesWhenTheServerRefuses() = runBlocking {
        serve("""{}""", code = 500)
        var navigated = false
        reportFinishThenNavigate(api, BaseItemDto(Id = "m1", Type = "Movie"), "u1", null, null, 0L) { navigated = true }
        assertTrue(navigated)
    }

    @Test fun openNextWaitsForBothWrites() = runBlocking {
        serve("""{}""")
        var opened = false
        reportStopThenOpenNext(api, BaseItemDto(Id = "e1", Type = "Episode"), "u1", null, null, 99L) { opened = true }
        assertTrue(opened)
        assertEquals(listOf("POST /Sessions/Playing/Stopped", "POST /UserPlayedItems/e1"), seen)
    }

    /** The dispose-time stop report claims the guard first; a finish path that already reported
     *  arms it, so no second stop can land after the watched mark and clear it. */
    @Test fun stopReportGuardAllowsExactlyOneSend() {
        val once = StopReportOnce()
        assertTrue(once.claim())
        assertFalse(once.claim())
        assertFalse(once.claim())
    }

    @Test fun finishArmsTheGuardSoDisposeSkips() = runBlocking {
        serve("""{}""")
        val once = StopReportOnce()
        var navigated = false
        reportFinishThenNavigate(api, BaseItemDto(Id = "m1", Type = "Movie"), "u1", "src", "sess", 1234L, once) { navigated = true }
        assertTrue(navigated)
        assertFalse("dispose would send a second stop report", once.claim())
        assertEquals(1, seen.count { it == "POST /Sessions/Playing/Stopped" })
    }

    @Test fun openNextArmsTheGuardSoDisposeSkips() = runBlocking {
        serve("""{}""")
        val once = StopReportOnce()
        reportStopThenOpenNext(api, BaseItemDto(Id = "e1", Type = "Episode"), "u1", null, null, 99L, once) { }
        assertFalse(once.claim())
        assertEquals(1, seen.count { it == "POST /Sessions/Playing/Stopped" })
    }

    /** The autoplay countdown and the up-next error path mark watched through the same ordered
     *  stop-then-mark write as every other finish path, with the guard armed. */
    @Test fun autoplayFinishStopsThenMarksAndArmsTheGuard() = runBlocking {
        serve("""{}""")
        val once = StopReportOnce()
        reportStopAndMarkPlayed(api, BaseItemDto(Id = "e9", Type = "Episode"), "u1", "src", "sess", 42L, once)
        assertEquals(listOf("POST /Sessions/Playing/Stopped", "POST /UserPlayedItems/e9"), seen)
        assertFalse(once.claim())
    }

    @Test fun autoplayFinishSkipsTheMarkForNonWatchableTypes() = runBlocking {
        serve("""{}""")
        val once = StopReportOnce()
        reportStopAndMarkPlayed(api, BaseItemDto(Id = "s1", Type = "Series"), "u1", null, null, 0L, once)
        assertEquals(listOf("POST /Sessions/Playing/Stopped"), seen)
        assertFalse(once.claim())
    }

    @Test fun lookupCrossesSeasonBoundaryInServerOrder() = runBlocking {
        serve("""{"Items":[{"Id":"s1e9","Type":"Episode","SeasonId":"S1"},{"Id":"s2e1","Type":"Episode","SeasonId":"S2"}]}""")
        val r = lookupUpNext(api, "series1", "u1", "s1e9")
        assertTrue(r is UpNextLookup.Found && r.next.Id == "s2e1")
    }

    @Test fun finishMarksOnlyTheStopReportForTrailer() = runBlocking {
        serve("""{}""")
        var navigated = false
        reportFinishThenNavigate(api, BaseItemDto(Id = "t1", Type = "Trailer"), "u1", null, null, 0L) { navigated = true }
        assertTrue(navigated)
        assertEquals(listOf("POST /Sessions/Playing/Stopped"), seen)
    }

    @Test fun hungServerStillNavigatesPromptly() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += "${request.method} ${request.path.orEmpty().substringBefore('?')}"
                return MockResponse().setHeader("Content-Type", "application/json").setBody("{}").setBodyDelay(8, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        var navigated = false
        val start = System.currentTimeMillis()
        reportFinishThenNavigate(api, BaseItemDto(Id = "m1", Type = "Movie"), "u1", null, null, 0L) { navigated = true }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(navigated)
        assertTrue("navigated in bounded time (${elapsed}ms)", elapsed < 14_000)
    }
}
