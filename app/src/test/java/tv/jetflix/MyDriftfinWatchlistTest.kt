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
    @Test fun watchlistAppliesFiltersButKeepsStarOrderByDefault() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Watchlist.toggle("aaa")
        Watchlist.toggle("bbb")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"aaa","Name":"A","Type":"Movie"}],"TotalRecordCount":1}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { vm.state.value.items.isNotEmpty() }
        server.takeRequest(); server.takeRequest() // consume unfiltered load + options
        // A filtered fetch: watched-only and a genre, like Favorites passes them.
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"aaa","Name":"A","Type":"Movie"}],"TotalRecordCount":1}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        vm.cycleWatch(JetflixTab.MY_LIST) // Unwatched
        vm.setGenre(JetflixTab.MY_LIST, "Drama")
        await { server.requestCount >= 4 }
        val req = server.takeRequest()
        val path = req.path!!
        org.junit.Assert.assertTrue(path, path.contains("filters=IsUnplayed") && path.contains("genres=Drama"))
        org.junit.Assert.assertFalse("no sort params until he picks one", path.contains("sortBy="))
    }

    @Test fun filteredOutTitleStaysStarred() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Watchlist.toggle("gone")
        Watchlist.toggle("kept")
        // Unfiltered load returns both; then a filtered load returns only one - no pruning allowed.
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"kept","Name":"K","Type":"Movie"},{"Id":"gone","Name":"G","Type":"Movie"}],"TotalRecordCount":2}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { vm.state.value.items.size == 2 }
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"kept","Name":"K","Type":"Movie"}],"TotalRecordCount":1}"""))
        vm.setGenre(JetflixTab.MY_LIST, "Drama")
        await { vm.state.value.items.size == 1 }
        assertEquals("filtered-out ids stay starred", listOf("gone", "kept").sorted(), Watchlist.ids.value.sorted())
    }

    @Test fun explicitSortReplacesStarOrder() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        Watchlist.toggle("aaa")
        Watchlist.toggle("bbb")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"aaa","Name":"A","Type":"Movie"},{"Id":"bbb","Name":"B","Type":"Movie"}],"TotalRecordCount":2}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        val vm = LibraryViewModel(repo, MemStore())
        vm.setSection(JetflixTab.MY_LIST, MySection.Watchlist)
        await { vm.state.value.items.isNotEmpty() }
        server.takeRequest(); server.takeRequest()
        // Server returns Z-first after he picks Date Added: the app must not re-rank into star order.
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"aaa","Name":"A","Type":"Movie"},{"Id":"bbb","Name":"B","Type":"Movie"}],"TotalRecordCount":2}"""))
        server.enqueue(MockResponse().setBody("""{"Genres":[],"Tags":[],"OfficialRatings":[],"Years":[]}"""))
        vm.setSort(JetflixTab.MY_LIST, tv.jetflix.app.ui.library.LibrarySort.Added)
        await { server.requestCount >= 3 }
        val req = server.takeRequest()
        val path = req.path!!
        org.junit.Assert.assertTrue(path, path.contains("sortBy=DateCreated"))
        await { vm.state.value.items.firstOrNull()?.Id == "aaa" }
        assertEquals("server order wins after an explicit sort", listOf("aaa", "bbb"), vm.state.value.items.map { it.Id })
    }
}