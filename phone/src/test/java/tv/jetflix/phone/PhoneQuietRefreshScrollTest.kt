package tv.jetflix.phone

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone Home LazyColumn while the quiet refresh swaps Continue Watching: a card the user has scrolled to must stay
 * where it is on screen after the refresh lands, whether the row only changes, appears above, or disappears above.
 * Drives the real HomeScreen and its 60 s refresh on the test clock, with the network answered by a local server.
 * If the appear/disappear cases fail, that is a real viewport jump.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneQuietRefreshScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val resumeCalls = AtomicInteger()
    @Volatile private var resumeItems = listOf("c1")

    private fun item(id: String, name: String, type: String) = """{"Id":"$id","Name":"$name","Type":"$type","ImageTags":{"Primary":"x"}}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return json("""{"Items":[],"TotalRecordCount":0}""")
                val path = url.encodedPath
                if (path.contains("/Images/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "X", "2000")))
                if (path.contains("/UserItems/Resume")) {
                    resumeCalls.incrementAndGet()
                    return json("""{"Items":[${resumeItems.joinToString(",") { item(it, "Resume $it", "Movie") }}],"TotalRecordCount":${resumeItems.size}}""")
                }
                if (path.contains("/Shows/NextUp")) return json("""{"Items":[${item("n1", "Next Episode", "Episode")}],"TotalRecordCount":1}""")
                if (path.contains("/Items/Latest")) return if (url.queryParameter("includeItemTypes") == "Episode") json("""[${item("e1", "Fresh Episode", "Episode")}]""") else json("""[${item("m1", "Fresh Film", "Movie")}]""")
                if (path.endsWith("/Items") && url.queryParameter("filters") == "IsFavorite") return json("""{"Items":[${item("f1", "Fave Film", "Movie")}],"TotalRecordCount":1}""")
                return json("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @After fun down() { server.shutdown() }

    private fun homeList() = hasScrollToIndexAction() and SemanticsMatcher("home list") { it.boundsInRoot.height > 1000f }

    /** Shows Home with [first] in Continue Watching, scrolls to the Favorites heading, lets a refresh return [after], and returns the heading's top before and after. */
    private fun scrollThenRefresh(first: List<String>, after: List<String>): Pair<Float, Float> {
        resumeItems = first
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodes(hasText("Recently Added Movies")).fetchSemanticsNodes().isNotEmpty() }
        awaitReal(30_000) {
            try { rule.onNode(homeList()).performScrollToNode(hasText("Favorites")); rule.onNodeWithText("Favorites").assertIsDisplayed(); true }
            catch (e: IllegalStateException) { if (e.message?.contains("attached to an owner") == true) false else throw e }
        }
        val before = rule.onNodeWithText("Favorites").getBoundsInRoot().top.value
        val calls = resumeCalls.get()
        resumeItems = after
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(61_000)
        awaitReal(30_000) { resumeCalls.get() > calls }
        repeat(10) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(60) }
        rule.mainClock.advanceTimeBy(500)
        val afterTop = rule.onNodeWithText("Favorites").getBoundsInRoot().top.value
        return before to afterTop
    }

    @Test fun cardStaysPutWhenContinueWatchingOnlyChanges() {
        val (b, a) = scrollThenRefresh(listOf("c1"), listOf("c2", "c1"))
        assertEquals("Favorites heading did not move", b, a, 1f)
    }

    @Test fun cardStaysPutWhenContinueWatchingAppearsAbove() {
        val (b, a) = scrollThenRefresh(emptyList(), listOf("c1"))
        assertEquals("Favorites heading did not move when the row appeared above it", b, a, 1f)
    }

    @Test fun cardStaysPutWhenContinueWatchingDisappearsAbove() {
        val (b, a) = scrollThenRefresh(listOf("c1"), emptyList())
        assertEquals("Favorites heading did not move when the row disappeared above it", b, a, 1f)
    }
}
