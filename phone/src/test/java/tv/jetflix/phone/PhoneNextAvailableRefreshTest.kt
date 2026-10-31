package tv.jetflix.phone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.EpisodeTarget
import tv.jetflix.phone.data.UserItemDataDto

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** After a finish marks an episode watched, the next fresh fetch drives the series page's Play target
 *  to the next available episode - the data-layer half of the completion refresh (the UI half is the
 *  details page's refetch on recompose). */
class PhoneNextAvailableRefreshTest {
    private val server = MockWebServer()

    @After fun tearDown() { runCatching { server.shutdown() } }

    @Test fun targetAdvancesPastNewlyWatchedEpisode() {
        val eps = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", UserData = UserItemDataDto(Played = true, LastPlayedDate = "2026-10-31T01:00:00Z")),
            BaseItemDto(Id = "e2", Type = "Episode"),
            BaseItemDto(Id = "e3", Type = "Episode"),
        )
        assertEquals("e2", EpisodeTarget.pick(eps)?.Id)
    }

    @Test fun targetCrossesSeasonBoundaryAfterSeasonFinale() {
        val eps = listOf(
            BaseItemDto(Id = "s1e9", Type = "Episode", UserData = UserItemDataDto(Played = true, LastPlayedDate = "2026-10-31T01:00:00Z")),
            BaseItemDto(Id = "s2e1", Type = "Episode"),
        )
        assertEquals("s2e1", EpisodeTarget.pick(eps)?.Id)
    }

    @Test fun freshFetchSeesServerSideWatchedMark() = runBlocking {
        var played = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return json.setBody(if (played) """{"Id":"e1","Type":"Episode","UserData":{"Played":true}}""" else """{"Id":"e1","Type":"Episode","UserData":{"Played":false}}""")
            }
        }
        Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
        val before = tv.jetflix.phone.data.Content.item("e1")
        assertTrue(before?.UserData?.Played == false)
        played = true // the watched mark has landed server-side
        val after = tv.jetflix.phone.data.Content.item("e1")
        assertTrue("no client-side cache: the next fetch sees the mark", after?.UserData?.Played == true)
        Unit
    }
}
