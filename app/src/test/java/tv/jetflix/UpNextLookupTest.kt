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
import tv.jetflix.app.ui.player.UpNextLookup
import tv.jetflix.app.ui.player.lookupUpNext

/** A failed next-episode lookup must read as Failed, never as the end of the series (Todd row 86). */
class UpNextLookupTest {
    private lateinit var server: MockWebServer
    private val two = """{"Items":[{"Id":"a","Type":"Episode"},{"Id":"b","Type":"Episode"}],"TotalRecordCount":2}"""

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun api() = JellyfinRepository().apiFor(server.url("/").toString().trimEnd('/'))

    @Test fun followingEpisodeIsFound() = runTest {
        server.enqueue(MockResponse().setBody(two))
        val out = lookupUpNext(api(), "series1", "u1", "a")
        assertTrue(out is UpNextLookup.Found)
        assertEquals("b", (out as UpNextLookup.Found).next.Id)
    }

    @Test fun lastEpisodeIsNone() = runTest {
        server.enqueue(MockResponse().setBody(two))
        assertEquals(UpNextLookup.None, lookupUpNext(api(), "series1", "u1", "b"))
    }

    @Test fun serverErrorIsFailedNotNone() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(UpNextLookup.Failed, lookupUpNext(api(), "series1", "u1", "a"))
    }

    @Test fun unreachableServerIsFailedNotNone() = runTest {
        server.shutdown() // nothing listening: the call fails outright
        assertEquals(UpNextLookup.Failed, lookupUpNext(api(), "series1", "u1", "a"))
    }
}
