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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.home.HomeRefresh
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeViewModel

/** The quiet Continue Watching / Next Up refresh: swaps only those rows, never blanks, never races a full load. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeContinueWatchingRefreshTest {
    private lateinit var server: MockWebServer
    private var resumeJson = ep("e1")
    private var resumeCode = 200
    private var resumeDelayMs = 0L
    private var nextUpJson = """{"Items":[]}"""
    private val resumeCalls = java.util.concurrent.atomic.AtomicInteger()

    private fun ep(vararg ids: String) = """{"Items":[${ids.joinToString(",") { """{"Id":"$it","Name":"Ep $it","Type":"Episode","UserData":{"PlaybackPositionTicks":100}}""" }}]}"""
    private fun json(b: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(b)

    @Before fun up() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SessionManager.signOut()
        AppSettings.setShowNextUp(true)
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val p = request.path!!
                    return when {
                        p.startsWith("/UserItems/Resume") -> {
                            resumeCalls.incrementAndGet()
                            if (resumeCode != 200) MockResponse().setResponseCode(resumeCode)
                            else json(resumeJson).also { if (resumeDelayMs > 0) it.setBodyDelay(resumeDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        }
                        p.startsWith("/Shows/NextUp") -> json(nextUpJson)
                        p.startsWith("/Items/Latest") -> json("""[{"Id":"m1","Name":"Movie One","Type":"Movie"}]""")
                        else -> json("""{"Items":[]}""")
                    }
                }
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "Alex", "token", "dev"))
    }

    @After fun down() { server.shutdown(); Dispatchers.resetMain(); SessionManager.signOut(); AppSettings.setShowNextUp(true) }

    private fun until(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 6000
        while (!cond() && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue(what, cond())
    }

    private fun loaded(): HomeViewModel {
        val v = HomeViewModel(JellyfinRepository())
        v.load()
        until("full load finished") { !v.state.value.loading && v.state.value.sections.isNotEmpty() }
        return v
    }

    private fun HomeViewModel.rowIds(title: String) = state.value.sections.firstOrNull { it.title == title }?.items?.map { it.Id }

    @Test fun mergePutsTheTwoRowsFirstAndKeepsTheRestUntouched() {
        val movies = HomeSection("Recently Added Movies", listOf(BaseItemDto(Id = "m1")), wide = false)
        val fav = HomeSection("Favorites", listOf(BaseItemDto(Id = "f1")), wide = false)
        val merged = HomeRefresh.merge(listOf(HomeSection("Continue Watching", listOf(BaseItemDto(Id = "old")), true), movies, fav), listOf(BaseItemDto(Id = "new")), listOf(BaseItemDto(Id = "n1")))
        assertEquals(listOf("Continue Watching", "Next Up", "Recently Added Movies", "Favorites"), merged.map { it.title })
        assertSame(movies, merged[2]); assertSame(fav, merged[3])
        assertEquals(listOf("new"), merged[0].items.map { it.Id })
    }

    @Test fun mergeDropsAnEmptyRowAndAddsANewOne() {
        val movies = HomeSection("Recently Added Movies", listOf(BaseItemDto(Id = "m1")), wide = false)
        val onlyMovies = HomeRefresh.merge(listOf(HomeSection("Continue Watching", listOf(BaseItemDto(Id = "a")), true), movies), emptyList(), emptyList())
        assertEquals(listOf("Recently Added Movies"), onlyMovies.map { it.title })
        val added = HomeRefresh.merge(onlyMovies, listOf(BaseItemDto(Id = "b")), emptyList())
        assertEquals(listOf("Continue Watching", "Recently Added Movies"), added.map { it.title })
    }

    @Test fun refreshSwapsContinueWatchingAndLeavesOtherRowsTheSameObjects() {
        val v = loaded()
        assertEquals(listOf("e1"), v.rowIds("Continue Watching"))
        val moviesBefore = v.state.value.sections.first { it.title == "Recently Added Movies" }
        resumeJson = ep("e2", "e1")
        v.refreshContinueWatching()
        until("row updated") { v.rowIds("Continue Watching") == listOf("e2", "e1") }
        assertSame("other rows are not rebuilt", moviesBefore, v.state.value.sections.first { it.title == "Recently Added Movies" })
        assertFalse(v.state.value.loading)
    }

    @Test fun unchangedAnswerPublishesNothing() {
        val v = loaded()
        val before = v.state.value
        val calls = resumeCalls.get()
        v.refreshContinueWatching()
        until("request made") { resumeCalls.get() > calls }
        Thread.sleep(150)
        assertSame("same state object, no recomposition", before, v.state.value)
    }

    @Test fun failedRequestKeepsTheRowInsteadOfBlankingIt() {
        val v = loaded()
        resumeCode = 500
        val calls = resumeCalls.get()
        v.refreshContinueWatching()
        until("request made") { resumeCalls.get() > calls }
        Thread.sleep(150)
        assertEquals(listOf("e1"), v.rowIds("Continue Watching"))
    }

    @Test fun watchedTitleDropsOutAndFinishedRowDisappears() {
        val v = loaded()
        resumeJson = """{"Items":[]}"""
        v.refreshContinueWatching()
        until("row gone") { v.rowIds("Continue Watching") == null }
        assertNotNull(v.rowIds("Recently Added Movies"))
    }

    @Test fun nextUpSettingOffKeepsNextUpOut() {
        AppSettings.setShowNextUp(false)
        val v = loaded()
        nextUpJson = ep("n9")
        resumeJson = ep("e3")
        v.refreshContinueWatching()
        until("resume updated") { v.rowIds("Continue Watching") == listOf("e3") }
        assertNull(v.rowIds("Next Up"))
    }

    @Test fun nextUpIsRefreshedWhenShown() {
        val v = loaded()
        nextUpJson = ep("n1")
        v.refreshContinueWatching()
        until("next up appears") { v.rowIds("Next Up") == listOf("n1") }
        assertEquals(listOf("Continue Watching", "Next Up"), v.state.value.sections.take(2).map { it.title })
    }

    @Test fun aFullLoadStartedWhileARefreshIsInFlightWins() {
        val v = loaded()
        resumeJson = ep("late"); resumeDelayMs = 500
        v.refreshContinueWatching()
        Thread.sleep(50)
        resumeJson = ep("fresh"); resumeDelayMs = 0
        v.load()
        until("full load applied") { v.rowIds("Continue Watching") == listOf("fresh") }
        Thread.sleep(800)
        assertEquals("the slow refresh must not overwrite the newer load", listOf("fresh"), v.rowIds("Continue Watching"))
    }

    @Test fun noRefreshBeforeTheFirstLoadFinishes() {
        val v = HomeViewModel(JellyfinRepository())
        val calls = resumeCalls.get()
        v.refreshContinueWatching()
        Thread.sleep(150)
        assertEquals(calls, resumeCalls.get())
    }
}
