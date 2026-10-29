package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.search.SearchViewModel

/** A failed search must surface as a note (like Home's load error), not a silent empty page (Todd row 86). */
@RunWith(RobolectricTestRunner::class)
class SearchFailureTest {
    private lateinit var server: MockWebServer
    private val main = UnconfinedTestDispatcher()
    @Volatile private var itemsCode = 200

    @Before fun setUp() {
        Dispatchers.setMain(main)
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().startsWith("/Items")) MockResponse().setResponseCode(itemsCode).setBody(if (itemsCode == 200) """{"Items":[]}""" else "")
                    else MockResponse().setBody("""{"Items":[]}""")
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "n", "t", "d"))
        tv.jetflix.app.data.AppSettings.setSearchRecommendations(false)
    }

    @After fun tearDown() {
        tv.jetflix.app.data.AppSettings.setSearchRecommendations(true)
        server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain()
    }

    private fun awaitRequests(n: Int) {
        var tries = 0
        while (server.requestCount < n && tries++ < 400) { main.scheduler.advanceUntilIdle(); Thread.sleep(25) }
    }

    @Test fun serverErrorMarksTheSearchFailedAndTypingAgainClearsIt() {
        itemsCode = 500
        val vm = SearchViewModel(JellyfinRepository())
        vm.onQueryChange("dune")
        main.scheduler.advanceTimeBy(2_000)
        awaitRequests(2)
        var tries = 0
        while (!vm.state.value.searchFailed && tries++ < 200) { main.scheduler.advanceUntilIdle(); Thread.sleep(25) }
        assertTrue("failed search is marked", vm.state.value.searchFailed)
        assertFalse(vm.state.value.searching)

        itemsCode = 200
        vm.onQueryChange("dune 2")
        assertFalse("a new query clears the failure note", vm.state.value.searchFailed)
    }

    @Test fun successfulSearchIsNotMarkedFailed() {
        val vm = SearchViewModel(JellyfinRepository())
        vm.onQueryChange("dune")
        main.scheduler.advanceTimeBy(2_000)
        awaitRequests(2)
        var tries = 0
        while (vm.state.value.searching && tries++ < 200) { main.scheduler.advanceUntilIdle(); Thread.sleep(25) }
        assertFalse(vm.state.value.searchFailed)
    }
}
