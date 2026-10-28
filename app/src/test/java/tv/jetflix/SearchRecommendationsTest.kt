package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.search.SearchViewModel

/** Idle Search recommendations: server suggestions first, top-rated unwatched as the fallback, posters only. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchRecommendationsTest {
    private lateinit var server: MockWebServer
    private var suggestionsCode = 200
    private val personTerms = mutableListOf<String>()

    private fun item(id: String, type: String, tag: String?) =
        """{"Id":"$id","Name":"$id","Type":"$type"${if (tag != null) ""","ImageTags":{"Primary":"$tag"}""" else ""}}"""

    @Before fun up() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SessionManager.signOut()
        AppSettings.setSearchRecommendations(true)
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path!!
                    return when {
                        path.startsWith("/Items/Suggestions") -> {
                            if (suggestionsCode != 200) return MockResponse().setResponseCode(suggestionsCode)
                            val type = request.requestUrl!!.queryParameter("type")
                            val body = when (type) {
                                "Movie" -> """{"Items":[${item("m1", "Movie", "t1")},${item("m2", "Movie", null)}]}"""
                                "Series" -> """{"Items":[${item("s1", "Series", "t2")}]}"""
                                else -> """{"Items":[]}"""
                            }
                            MockResponse().setBody(body)
                        }
                        path.startsWith("/Persons") -> {
                            personTerms += request.requestUrl!!.queryParameter("searchTerm") ?: ""
                            MockResponse().setBody("""{"Items":[{"Id":"p1","Name":"Tom Actor","Type":"Person","ImageTags":{"Primary":"pt"}}]}""")
                        }
                        path.startsWith("/Items") -> MockResponse().setBody("""{"Items":[${item("top1", "Movie", "t3")},${item("top2", "Movie", "t4")}]}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "Alex", "token", "dev"))
    }

    @After fun down() { server.shutdown(); Dispatchers.resetMain(); SessionManager.signOut(); AppSettings.setSearchRecommendations(true) }

    private fun awaitRows(v: SearchViewModel) {
        val end = System.currentTimeMillis() + 5000
        while (v.state.value.recommendations.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
    }

    @Test fun suggestionsFeedMovieAndShowRowsPostersOnly() {
        val v = SearchViewModel(JellyfinRepository())
        awaitRows(v)
        val rows = v.state.value.recommendations
        assertEquals(listOf("Recommended Movies", "Recommended Shows"), rows.map { it.first })
        assertEquals(listOf("m1"), rows[0].second.map { it.Id }) // m2 has no poster and is dropped
        assertEquals(listOf("s1"), rows[1].second.map { it.Id })
    }

    @Test fun fallsBackToTopRatedUnwatchedWhenSuggestionsAreUnavailable() {
        suggestionsCode = 404
        val v = SearchViewModel(JellyfinRepository())
        awaitRows(v)
        val rows = v.state.value.recommendations
        assertEquals(listOf("Recommended Movies", "Recommended Shows"), rows.map { it.first })
        assertEquals(listOf("top1", "top2"), rows[0].second.map { it.Id })
    }

    @Test fun settingOffLoadsNoRows() {
        AppSettings.setSearchRecommendations(false)
        val v = SearchViewModel(JellyfinRepository())
        Thread.sleep(300)
        assertTrue(v.state.value.recommendations.isEmpty())
    }

    @Test fun searchFindsActorsByNameAlongsideTitles() {
        val v = SearchViewModel(JellyfinRepository())
        awaitRows(v)
        v.onQueryChange("tom"); v.searchNow()
        val end = System.currentTimeMillis() + 5000
        while (v.state.value.people.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(listOf("p1"), v.state.value.people.map { it.Id })
        assertEquals("tom", personTerms.last())
        assertTrue("title results still come back", v.state.value.results.isNotEmpty())
        assertEquals("tom", v.state.value.query)
    }

    @Test fun clearingTheQueryClearsActors() {
        val v = SearchViewModel(JellyfinRepository())
        awaitRows(v)
        v.onQueryChange("tom"); v.searchNow()
        val end = System.currentTimeMillis() + 5000
        while (v.state.value.people.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20)
        v.onQueryChange("")
        assertTrue(v.state.value.people.isEmpty() && v.state.value.results.isEmpty())
    }
}
