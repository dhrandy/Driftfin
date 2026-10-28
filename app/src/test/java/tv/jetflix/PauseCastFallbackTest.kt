package tv.jetflix

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
import tv.jetflix.app.data.loadPauseCast

class PauseCastFallbackTest {
    private lateinit var server: MockWebServer
    private val repo = JellyfinRepository()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun api() = repo.apiFor(server.url("/").toString().trimEnd('/'))

    @Test fun episodeActorsWinSeriesIsNotFetched() = runTest {
        server.enqueue(MockResponse().setBody("""{"People":[{"Name":"Ep Actor","Id":"e1","Type":"Actor"}]}"""))
        val cast = loadPauseCast(api(), "u", BaseItemDto(Id = "ep", SeriesId = "ser"))
        assertEquals(listOf("Ep Actor"), cast.map { it.Name })
        assertEquals(1, server.requestCount)
    }

    @Test fun emptyEpisodeCastFallsBackToSeries() = runTest {
        // Marshals S01E01 shape: People populated but writer-only.
        server.enqueue(MockResponse().setBody("""{"People":[{"Name":"Show Runner","Id":"w1","Type":"Writer"}]}"""))
        server.enqueue(MockResponse().setBody("""{"People":[{"Name":"Series Star","Id":"a1","Type":"Actor"},{"Name":"Series Writer","Id":"w2","Type":"Writer"}]}"""))
        val cast = loadPauseCast(api(), "u", BaseItemDto(Id = "ep", SeriesId = "ser"))
        assertEquals(listOf("Series Star"), cast.map { it.Name })
        val req = server.takeRequest(); assertTrue(req.path!!.contains("Items/ep"))
        val req2 = server.takeRequest(); assertTrue(req2.path!!.contains("Items/ser"))
    }

    @Test fun movieWithoutSeriesIdStaysEmpty() = runTest {
        server.enqueue(MockResponse().setBody("""{"People":[{"Name":"Crew","Id":"c1","Type":"Director"}]}"""))
        val cast = loadPauseCast(api(), "u", BaseItemDto(Id = "mov"))
        assertTrue(cast.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun serverErrorYieldsEmptyCast() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        val cast = loadPauseCast(api(), "u", BaseItemDto(Id = "ep", SeriesId = "ser"))
        assertTrue(cast.isEmpty())
    }

    @Test fun castCappedAtFifteen() = runTest {
        val people = (1..20).joinToString(",") { """{"Name":"A$it","Id":"a$it","Type":"Actor"}""" }
        server.enqueue(MockResponse().setBody("""{"People":[$people]}"""))
        val cast = loadPauseCast(api(), "u", BaseItemDto(Id = "ep"))
        assertEquals(15, cast.size)
        assertEquals(1, server.requestCount)
    }
}
