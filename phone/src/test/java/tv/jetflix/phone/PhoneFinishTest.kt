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
import tv.jetflix.phone.ui.apiMarkPlayed
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

    @Test fun countdownMarkPostsPlayed() {
        serve("""{}""")
        apiMarkPlayed(api, BaseItemDto(Id = "e9", Type = "Episode"), "u1")
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && seen.none { it.contains("UserPlayedItems/e9") }) Thread.sleep(50)
        Thread.sleep(500)
        assertEquals(1, seen.count { it == "POST /UserPlayedItems/e9" })
    }

    @Test fun countdownMarkSkipsNonWatchableTypes() {
        serve("""{}""")
        apiMarkPlayed(api, BaseItemDto(Id = "s1", Type = "Series"), "u1")
        Thread.sleep(1500)
        assertTrue(seen.isEmpty())
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
