package tv.jetflix.phone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.Sort
import tv.jetflix.phone.data.Watchlist

/** The saved watchlist ids must survive a partial, empty, failed or offline server answer. Only the user's own
 *  un-star removes an id. Synthetic ids and titles only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneWatchlistKeepsIdsTest {
    private lateinit var server: MockWebServer
    private lateinit var url: String
    @Volatile private var mode = "partial"

    @Before fun setUp() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        Watchlist.resetForTest()
        Watchlist.init(ctx)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (mode) {
                "error" -> MockResponse().setResponseCode(500)
                "empty" -> MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}""")
                else -> MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"Items":[{"Id":"a","Name":"Alpha","Type":"Movie"}],"TotalRecordCount":1}""")
            }
        }
        server.start()
        url = server.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(url, "u", "N", "t", Graph.tokens.deviceId()))
        Watchlist.setUser(url, "u")
        Watchlist.toggle("a"); Watchlist.toggle("b"); Watchlist.toggle("c")
    }

    private val saved = listOf("c", "b", "a") // newest star first

    private fun assertIdsKept() {
        assertEquals(saved, Watchlist.ids.value)
        Watchlist.setUser(url, "u") // reload from storage: what is on disk, not only what is in memory
        assertEquals(saved, Watchlist.ids.value)
    }

    @Test fun partialAnswerShowsWhatCameBackAndKeepsEveryId() = runBlocking {
        mode = "partial"
        assertEquals(listOf("Alpha"), Content.watchlistOrNull(Sort.Added)?.map { it.Name })
        assertIdsKept()
    }

    @Test fun emptyAnswerKeepsEveryId() = runBlocking {
        mode = "empty"
        assertEquals(emptyList<String>(), Content.watchlistOrNull(Sort.Added)?.map { it.Id })
        assertIdsKept()
    }

    @Test fun httpErrorIsAFailureNotAnEmptyListAndKeepsEveryId() = runBlocking {
        mode = "error"
        assertNull(Content.watchlistOrNull(Sort.Added))
        assertIdsKept()
    }

    @Test fun offlineIsAFailureAndKeepsEveryId() = runBlocking {
        server.shutdown()
        assertNull(Content.watchlistOrNull(Sort.Added))
        assertIdsKept()
    }

    @Test fun userUnstarStillRemovesAndPersists() = runBlocking {
        mode = "partial"
        Content.watchlistOrNull(Sort.Added)
        assertEquals(false, Watchlist.toggle("b")) // un-star
        assertEquals(listOf("c", "a"), Watchlist.ids.value)
        Watchlist.setUser(url, "u")
        assertEquals(listOf("c", "a"), Watchlist.ids.value)
        assertNotNull(Content.watchlistOrNull(Sort.Added))
    }

    @After fun tearDown() { runCatching { server.shutdown() }; Graph.signOut() }
}
