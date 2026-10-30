package tv.jetflix.phone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
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

/** Watchlist sorting happens in the app, so the fetch has to ask for the fields the sort reads.
 *  Without them every item compares equal and the server's alphabetical order survives
 *  (reported 10/29: "I tried Year sort and it sorted by alphabetical"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneWatchlistSortTest {
    private lateinit var server: MockWebServer

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
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Like the real server, only the asked-for fields come back, newest id last alphabetically.
                val fields = request.requestUrl?.queryParameter("fields").orEmpty()
                fun item(id: String, name: String, year: Int, rating: String) =
                    """{"Id":"$id","Name":"$name","Type":"Movie"""" +
                        (if (fields.contains("ProductionYear")) ""","ProductionYear":$year""" else "") +
                        (if (fields.contains("CommunityRating")) ""","CommunityRating":$rating""" else "") + "}"
                val items = item("a", "Alpha", 2001, "7.1") + "," + item("b", "Middle", 1999, "8.2") + "," + item("c", "Zulu", 2024, "6.3")
                return MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"Items":[$items],"TotalRecordCount":3}""")
            }
        }
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(url, "u", "N", "t", Graph.tokens.deviceId()))
        Watchlist.setUser(url, "u")
        Watchlist.toggle("a"); Watchlist.toggle("b"); Watchlist.toggle("c")
    }

    @Test fun yearSortOrdersNewestFirstNotAlphabetical() = runBlocking {
        val items = Content.watchlist(Sort.Year)
        assertEquals(listOf("Zulu", "Alpha", "Middle"), items.map { it.Name })
    }

    @Test fun ratingSortOrdersBestFirst() = runBlocking {
        val items = Content.watchlist(Sort.Rating)
        assertEquals(listOf("Middle", "Alpha", "Zulu"), items.map { it.Name })
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
