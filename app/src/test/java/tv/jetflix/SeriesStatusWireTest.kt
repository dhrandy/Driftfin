package tv.jetflix

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.details.genreLine
import tv.jetflix.app.ui.details.seriesStatusLabel

/**
 * TV46 report: Continuing/Ended never appeared on a real server. The seed-DTO tests could not
 * catch it because Jellyfin sends the status as "Status" (DtoService sets dto.Status for every series,
 * unconditionally, on 10.8/10.9/10.10/master); the wire has no "SeriesStatus" key. These tests exercise
 * the real request/response path with the real server payload shape.
 */
class SeriesStatusWireTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun api() = JellyfinRepository().apiFor(server.url("/").toString().trimEnd('/'))

    @Test fun wireStatusKeyDeserializesIntoSeriesStatus() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"Id":"s1","Type":"Series","Name":"Some Show","Genres":["Drama","Comedy"],"Status":"Continuing"}"""
        ))
        val item = api().getItem("s1", "u1").body()!!
        assertEquals("Continuing", item.SeriesStatus)
        assertEquals("Continuing", seriesStatusLabel(item.SeriesStatus))
        val line = genreLine(item, null)
        assertTrue("genre line carries the status: $line", line.contains("Continuing"))
        assertTrue("genre line keeps the genres: $line", line.contains("Drama"))
    }

    @Test fun endedAndCanceledMapFromWire() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id":"s1","Type":"Series","Status":"Ended"}"""))
        assertEquals("Ended", seriesStatusLabel(api().getItem("s1", "u1").body()!!.SeriesStatus))
        server.enqueue(MockResponse().setBody("""{"Id":"s1","Type":"Series","Status":"Canceled"}"""))
        assertEquals("Canceled", seriesStatusLabel(api().getItem("s1", "u1").body()!!.SeriesStatus))
    }

    @Test fun missingStatusRendersNothingButKeepsGenres() = runTest {
        server.enqueue(MockResponse().setBody("""{"Id":"s1","Type":"Series","Genres":["Drama"]}"""))
        val item = api().getItem("s1", "u1").body()!!
        assertEquals("Drama", genreLine(item, null))
    }
}
