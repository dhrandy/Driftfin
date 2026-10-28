package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import org.robolectric.RuntimeEnvironment
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.Watchlist
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.MySection
import tv.jetflix.app.ui.library.LibraryPrefsStore
import tv.jetflix.app.ui.library.LibraryViewModel

/** The My Driftfin Watchlist section: order, ids fetch, star order, empty list without a server call. */
@RunWith(RobolectricTestRunner::class)
class MyDriftfinWatchlistTest {
    private lateinit var server: MockWebServer
    private val repo = JellyfinRepository()

    private class MemStore : LibraryPrefsStore {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        Watchlist.resetForTest()
        Watchlist.init(RuntimeEnvironment.getApplication())
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "n", "t", "d"))
    }

    @After fun tearDown() {
        server.shutdown()
        SessionManager.signOut()
        Dispatchers.resetMain()
        Watchlist.resetForTest()
    }

    /** Drives the test scheduler while giving OkHttp's real callback threads time to land between advances. */
    private fun kotlinx.coroutines.test.TestScope.await(done: () -> Boolean) {
        var tries = 0
        while (!done() && tries++ < 200) { advanceUntilIdle(); Thread.sleep(25) }
    }

    @Test fun sectionOrderIsFavoritesWatchlistCollections() {
        assertEquals(listOf(MySection.Favorites, MySection.Watchlist, MySection.Collections), MySection.values().toList())
    }

    @Test fun watchlistFetchesStarredIdsInStarOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Watchlist.toggle("aaa")
        Watchlist.toggle("bbb") // starred after aaa, so it leads
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"aaa","Name":"A","Type":"Movie"},{"Id":"bbb","Name":"B","Type":"Movie"}],"TotalRecordCount":2}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { vm.state.value.items.isNotEmpty() }
        assertEquals(listOf("bbb", "aaa"), vm.state.value.items.map { it.Id })
        val req = server.takeRequest()
        val path = req.path!!
        assertTrue(path, path.contains("/Items?") && path.contains("ids=bbb%2Caaa"))
    }

    @Test fun deletedOnServerDropsOffTheList() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Watchlist.toggle("gone")
        Watchlist.toggle("kept")
        server.enqueue(MockResponse().setBody("{\"Items\":[{\"Id\":\"kept\",\"Name\":\"K\",\"Type\":\"Movie\"}],\"TotalRecordCount\":1}"))
        server.enqueue(MockResponse().setBody("{\"Genres\":[],\"Tags\":[],\"OfficialRatings\":[],\"Years\":[]}"))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { vm.state.value.items.isNotEmpty() }
        assertEquals(listOf("kept"), vm.state.value.items.map { it.Id })
        assertEquals("the dead id is pruned from the store itself", listOf("kept"), Watchlist.ids.value)
    }

    @Test fun emptyWatchlistDoesNotAskTheServerForItems() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { !vm.state.value.loading }
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(null, vm.state.value.error)
        val req = server.takeRequest()
        assertTrue("only the filter-options call may run", req.path!!.startsWith("/Items/Filters"))
    }
}
