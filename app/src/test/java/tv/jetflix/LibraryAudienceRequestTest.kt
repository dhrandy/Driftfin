package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.Audience
import tv.jetflix.app.ui.library.LibraryPrefsStore
import tv.jetflix.app.ui.library.LibraryViewModel

/** The requests the Audience modes actually send (parentId and includeItemTypes), against a fake Jellyfin. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAudienceRequestTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var views = listOf("Movies" to "movies", "Kids Movies" to "movies", "TV Shows" to "tvshows", "Kids Shows" to "tvshows")

    private class MemStore : LibraryPrefsStore {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    val parent = Regex("parentId=([^&]+)").find(path)?.groupValues?.get(1)?.replace("%20", " ")
                    val types = Regex("includeItemTypes=([^&]+)").find(path)?.groupValues?.get(1)
                    return when {
                        path.contains("Views") -> MockResponse().setBody("""{"Items":[${views.joinToString(",") { """{"Id":"${it.first}","Name":"${it.first}","Type":"CollectionFolder","CollectionType":"${it.second}"}""" }}]}""")
                        parent != null && types != null -> {
                            // Like Jellyfin: a library only returns items of its own kind for the asked type.
                            val kind = if (views.firstOrNull { it.first == parent }?.second == "movies") "Movie" else "Series"
                            val hit = types.split(",").contains(kind)
                            MockResponse().setBody(if (hit) """{"Items":[{"Id":"item-$parent","Name":"From $parent","Type":"$kind"}],"TotalRecordCount":1}""" else """{"Items":[],"TotalRecordCount":0}""")
                        }
                        else -> MockResponse().setBody("""{"Items":[]}""")
                    }
                }
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "nu1", "tok", "dev"))
    }
    @After fun tearDown() { server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain() }

    private fun itemRequests() = requests.filter { Regex("parentId=").containsMatchIn(it.path.orEmpty()) && it.path!!.contains("includeItemTypes=") && !it.path!!.contains("Limit=1&") }

    private fun kotlinx.coroutines.test.TestScope.run(tab: JetflixTab, a: Audience): LibraryViewModel {
        val vm = LibraryViewModel(JellyfinRepository(), MemStore())
        vm.restorePrefs("u1", tab)
        if (a == Audience.All) vm.load(tab) else vm.setAudience(tab, a)
        repeat(40) { advanceUntilIdle(); if (!vm.state.value.loading) return vm; Thread.sleep(50) }
        return vm
    }

    @Test fun noKidsOnMoviesAsksOnlyTheRegularMoviesLibraryForMovies() = runTest {
        val vm = run(JetflixTab.MOVIES, Audience.NoKids)
        assertTrue(itemRequests().all { it.path!!.contains("includeItemTypes=Movie") })
        assertEquals(listOf("item-Movies"), vm.state.value.items.map { it.Id })
    }

    @Test fun kidsOnlyOnShowsAsksOnlyKidsShowsForSeries() = runTest {
        val vm = run(JetflixTab.SHOWS, Audience.KidsOnly)
        assertTrue(itemRequests().all { it.path!!.contains("includeItemTypes=Series") })
        assertEquals(listOf("item-Kids Shows"), vm.state.value.items.map { it.Id })
    }

    @Test fun allOnMoviesAsksBothMovieLibrariesAndNeverTheShowsOnes() = runTest {
        val vm = run(JetflixTab.MOVIES, Audience.All)
        val libs = itemRequests().map { Regex("parentId=([^&]+)").find(it.path!!)!!.groupValues[1].replace("%20", " ") }.toSet()
        assertTrue("Movies" in libs && "Kids Movies" in libs)
        assertEquals(setOf("item-Movies", "item-Kids Movies"), vm.state.value.items.map { it.Id }.toSet())
    }

    @Test fun kidsOnlyWithNoKidsLibraryShowsAMessageNotEverything() = runTest {
        views = listOf("Movies" to "movies", "TV Shows" to "tvshows")
        val vm = run(JetflixTab.MOVIES, Audience.KidsOnly)
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(vm.state.value.error.orEmpty().contains("No Kids"))
        assertTrue("no item request may be sent", itemRequests().isEmpty())
    }

    @Test fun kidsOnlyOnMoviesAsksKidsMoviesForMovie() = runTest {
        val vm = run(JetflixTab.MOVIES, Audience.KidsOnly)
        assertTrue(itemRequests().all { it.path!!.contains("includeItemTypes=Movie") })
        assertEquals(listOf("item-Kids Movies"), vm.state.value.items.map { it.Id })
    }

    @Test fun noKidsOnShowsAsksOnlyRegularShowsForSeries() = runTest {
        val vm = run(JetflixTab.SHOWS, Audience.NoKids)
        assertTrue(itemRequests().all { it.path!!.contains("includeItemTypes=Series") })
        assertEquals(listOf("item-TV Shows"), vm.state.value.items.map { it.Id })
    }
}
