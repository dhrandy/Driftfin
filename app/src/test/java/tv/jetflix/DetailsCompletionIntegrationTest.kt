package tv.jetflix

import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.PlaybackStopInfo
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.details.DetailsViewModel
import tv.jetflix.app.ui.details.playLabel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end completion state: the page opens on a half-watched movie (Resume), the player
 * reports natural end (stop report + markPlayed, awaited in that order exactly as PlayerScreen
 * does), and after invalidate()+reload the refetched page is Play with the watched flag set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailsCompletionIntegrationTest {
    private lateinit var server: MockWebServer
    private val played = AtomicBoolean(false)
    private val itemFetches = AtomicInteger(0)
    private val stopReports = AtomicInteger(0)
    private val playedPosts = AtomicInteger(0)

    private fun movieJson(): String {
        val userData = if (played.get())
            """"UserData":{"Played":true,"PlaybackPositionTicks":0,"PlayCount":1}"""
        else
            """"UserData":{"Played":false,"PlaybackPositionTicks":27000000000}"""
        return """{"Id":"m1","Name":"Half Watched","Type":"Movie","RunTimeTicks":54000000000,"ProductionYear":2024,$userData}"""
    }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        played.set(false); itemFetches.set(0); stopReports.set(0); playedPosts.set(0)
        SessionManager.signOut()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    p.contains("Sessions/Playing/Stopped") -> { stopReports.incrementAndGet(); json.setBody("{}") }
                    p.contains("UserPlayedItems/m1") && request.method == "POST" -> { playedPosts.incrementAndGet(); played.set(true); json.setBody("""{"Played":true,"PlayCount":1}""") }
                    p.contains("/Similar") -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                    p.startsWith("/Items/m1") -> { itemFetches.incrementAndGet(); json.setBody(movieJson()) }
                    else -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                }
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "nu1", "tok", "dev"))
    }

    @After fun tearDown() { server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain() }

    @Test fun naturalEndFlipsDetailsFromResumeToPlayedPlay() = runTest {
        val repo = JellyfinRepository()
        val vm = DetailsViewModel(repo)
        vm.load("m1")
        repeat(60) { advanceUntilIdle(); if (vm.state.value.item != null) return@repeat; Thread.sleep(50) }
        Thread.sleep(300); advanceUntilIdle()
        val initial = vm.state.value.item!!
        assertEquals(1, itemFetches.get())
        assertTrue("opens half-watched", initial.isResumeable)
        assertFalse(initial.UserData?.Played == true)
        assertEquals("Resume", playLabel(initial))

        // What PlayerScreen now does on STATE_ENDED, in order, both awaited before any reload.
        val api = repo.apiFor(server.url("/").toString().trimEnd('/'))
        api.reportPlaybackStopped(PlaybackStopInfo(ItemId = "m1", MediaSourceId = "ms", PlaySessionId = "ps", PositionTicks = 53_900_000_000L))
        api.markPlayed("m1", "u1")
        assertEquals(1, stopReports.get())
        assertEquals(1, playedPosts.get())

        // Returning to the page invalidates and refetches; the fresh page is watched, no resume.
        vm.invalidate()
        vm.load("m1")
        repeat(60) { advanceUntilIdle(); if (itemFetches.get() >= 2 && vm.state.value.item?.UserData?.Played == true) return@repeat; Thread.sleep(50) }
        Thread.sleep(300); advanceUntilIdle()
        val after = vm.state.value.item!!
        assertEquals("reload must refetch from the server", 2, itemFetches.get())
        assertTrue("refetched movie is watched", after.UserData?.Played == true)
        assertFalse("no resume position left", after.isResumeable)
        assertEquals("Play", playLabel(after))
        assertTrue("Play button points at the movie itself", vm.state.value.playTarget?.Id == "m1")
    }
}
