package tv.jetflix

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.player.earnsWatchedAtEnd
import tv.jetflix.app.ui.player.reportFinishThenNavigate
import tv.jetflix.app.ui.player.reportStopThenOpenNext

/** Completion paths: the page after a natural finish must load only AFTER the server took the stop
 *  report AND the watched mark, so a finished movie/episode shows Play and the red check - never
 *  Resume - even when the server is slow to answer. */
class PlayerFinishReportTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun api() = JellyfinRepository().apiFor(server.url("/").toString().trimEnd('/'))
    private val episode = BaseItemDto(Id = "ep1", Type = "Episode", SeriesId = "s1")
    private val movie = BaseItemDto(Id = "mov1", Type = "Movie")
    private val trailer = BaseItemDto(Id = "tr1", Type = "Trailer")

    @Test fun nextEpisodePageOpensOnlyAfterTheStopReportAndPlayedMarkLand() = runTest {
        // A slow server: the answers are delayed, like reports still being processed.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(400, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var requestsWhenOpened = -1
        reportStopThenOpenNext(api(), episode, "user1", "ms1", "ps1", 42L * 600_000_000) {
            requestsWhenOpened = server.requestCount
        }
        // Navigation happened exactly once, and only after the server had both writes.
        assertEquals(2, requestsWhenOpened)
        val stop = server.takeRequest()
        assertEquals("POST", stop.method)
        assertTrue(stop.path!!.startsWith("/Sessions/Playing/Stopped"))
        assertTrue(stop.body.readUtf8().contains("\"ItemId\":\"ep1\""))
        val mark = server.takeRequest()
        assertEquals("POST", mark.method)
        assertTrue(mark.path!!.startsWith("/UserPlayedItems/ep1"))
        assertTrue(mark.path!!.contains("userId=user1"))
    }

    @Test fun movieFinishMarksPlayedBeforeNavigating() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var requestsWhenNavigated = -1
        reportFinishThenNavigate(api(), movie, "user1", "ms1", "ps1", 25200_000_000_000L) {
            requestsWhenNavigated = server.requestCount
        }
        assertEquals(2, requestsWhenNavigated)
        val stop = server.takeRequest()
        assertTrue(stop.path!!.startsWith("/Sessions/Playing/Stopped"))
        assertTrue(stop.body.readUtf8().contains("\"PositionTicks\":25200000000000"))
        val mark = server.takeRequest()
        assertTrue(mark.path!!.startsWith("/UserPlayedItems/mov1"))
    }

    @Test fun failedStopReportStillNavigatesAndStillMarks() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var opened = false
        reportStopThenOpenNext(api(), episode, "user1", null, null, 0L) { opened = true }
        assertTrue(opened)
        assertEquals(2, server.requestCount)
    }

    @Test fun failedPlayedMarkStillNavigates() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500))
        var navigated = false
        reportFinishThenNavigate(api(), movie, "user1", null, null, 0L) { navigated = true }
        assertTrue(navigated)
        assertEquals(2, server.requestCount)
    }

    @Test fun trailerFinishReportsStopButNeverMarksPlayed() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        var navigated = false
        reportFinishThenNavigate(api(), trailer, "user1", null, null, 0L) { navigated = true }
        assertTrue(navigated)
        // Only the stop report went out; a trailer never earns the watched check.
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/Sessions/Playing/Stopped"))
    }

    @Test fun onlyMoviesAndEpisodesEarnTheCheck() {
        assertTrue(earnsWatchedAtEnd(movie))
        assertTrue(earnsWatchedAtEnd(episode))
        assertTrue(!earnsWatchedAtEnd(trailer))
        assertTrue(!earnsWatchedAtEnd(BaseItemDto(Id = "v1", Type = "Video")))
    }

    @Test fun stopReportCarriesTheFinalPositionSoTheServerClearsResume() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        reportStopThenOpenNext(api(), episode, "user1", "ms1", "ps1", 25200_000_000_000L) { }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"PositionTicks\":25200000000000"))
    }
}
