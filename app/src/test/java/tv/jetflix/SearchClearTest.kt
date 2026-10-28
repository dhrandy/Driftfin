package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.search.SearchViewModel

/**
 * Randy on build 25: "search bar should clear if you go away and back to it". The screen clears
 * through SearchViewModel.clear(); this pins the state reset and that a pending debounced search
 * is cancelled with it. Details-over-Search keeps the stack entry, so Back from a result keeps
 * the query (wired in MainActivity, not reset here).
 */
@RunWith(RobolectricTestRunner::class)
class SearchClearTest {
    private lateinit var server: MockWebServer
    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(main)
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("""{"Items":[]}"""))
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "n", "t", "d"))
        // Keep the idle-page recommendation load out of this test: it would fire its own HTTP
        // requests on ViewModel init and break the request-count assertions for clear().
        tv.jetflix.app.data.AppSettings.setSearchRecommendations(false)
    }

    @After fun tearDown() {
        tv.jetflix.app.data.AppSettings.setSearchRecommendations(true)
        server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain()
    }

    @Test fun clearEmptiesTheBarAndCancelsThePendingSearch() {
        val vm = SearchViewModel(JellyfinRepository())
        vm.onQueryChange("dune")
        assertEquals("dune", vm.state.value.query)

        vm.clear()
        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.results.isEmpty())

        // The debounced fetch for the old query must never fire after the clear.
        main.scheduler.advanceTimeBy(2_000)
        assertEquals(0, server.requestCount)
    }

    @Test fun queryLeftStandingDoesSearch() {
        // Control: without clear(), the same debounce fires the search request.
        val vm = SearchViewModel(JellyfinRepository())
        vm.onQueryChange("dune")
        main.scheduler.advanceTimeBy(2_000)
        var tries = 0
        while (server.requestCount == 0 && tries++ < 200) { main.scheduler.advanceUntilIdle(); Thread.sleep(25) }
        assertEquals(1, server.requestCount)
    }
}
