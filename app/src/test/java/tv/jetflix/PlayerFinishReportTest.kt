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
import tv.jetflix.app.ui.player.reportStopThenOpenNext

/** Autoplay-off completion: the next episode's page must open only AFTER the server took the stop
 *  report, so the fresh page's episode list already shows the finished episode's red check - even
 *  when the server is slow to answer. */
class PlayerFinishReportTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun api() = JellyfinRepository().apiFor(server.url("/").toString().trimEnd('/'))
    private val episode = BaseItemDto(Id = "ep1", Type = "Episode", SeriesId = "s1")

    @Test fun nextEpisodePageOpensOnlyAfterTheStopReportLands() = runTest {
        // A slow server: the answer is delayed, like a stop report still being processed.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(400, TimeUnit.MILLISECONDS))
        var requestsWhenOpened = -1
        reportStopThenOpenNext(api(), episode, "ms1", "ps1", 42L * 600_000_000) {
            requestsWhenOpened = server.requestCount
        }
        // Navigation happened exactly once, and only after the server had the report.
        assertEquals(1, requestsWhenOpened)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.startsWith("/Sessions/Playing/Stopped"))
        assertTrue(recorded.body.readUtf8().contains("\"ItemId\":\"ep1\""))
    }

    @Test fun failedStopReportStillNavigatesWithoutMarkingAnythingLocally() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        var opened = false
        reportStopThenOpenNext(api(), episode, null, null, 0L) { opened = true }
        assertTrue(opened)
        assertEquals(1, server.requestCount)
    }

    @Test fun stopReportCarriesTheFinalPositionSoTheServerMarksItPlayed() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        reportStopThenOpenNext(api(), episode, "ms1", "ps1", 25200_000_000_000L) { }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"PositionTicks\":25200000000000"))
    }
}
