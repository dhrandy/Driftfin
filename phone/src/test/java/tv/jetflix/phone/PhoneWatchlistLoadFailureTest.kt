package tv.jetflix.phone

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import tv.jetflix.phone.data.Watchlist
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.MyStuffScreen

/** My Stuff > Watchlist when the server answer fails: an error with Retry, the saved list untouched (memory and
 *  stored prefs, same server and user key), shown titles kept, and Retry loads them again. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneWatchlistLoadFailureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var url: String
    @Volatile private var failing = false
    @Volatile private var emptyAnswer = false

    private fun hasButtonRole() = androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Button)
    private val cover = hasClickAction() and !hasButtonRole() and
        !hasText("Favorites") and !hasText("Watchlist") and !hasText("Collections") and !hasText("Retry")

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        Watchlist.resetForTest()
        Watchlist.init(ctx)
        ctx.getSharedPreferences("driftfin_phone_browse", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.contains("/Images/")) return MockResponse().setResponseCode(404)
                if (failing && path.contains("ids=")) return MockResponse().setResponseCode(500)
                if (emptyAnswer && path.contains("ids=")) return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}""")
                val body = if (path.contains("ids=")) {
                    """{"Items":[{"Id":"a","Name":"Alpha","Type":"Movie"},{"Id":"b","Name":"Beta","Type":"Series"}],"TotalRecordCount":2}"""
                } else """{"Items":[],"TotalRecordCount":0}"""
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        url = server.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(url, "u", "N", "t", Graph.tokens.deviceId()))
        Watchlist.setUser(url, "u")
        Watchlist.toggle("a"); Watchlist.toggle("b")
    }

    private fun stored(): String? =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("jetflix_watchlist", android.content.Context.MODE_PRIVATE).getString("$url#u", null)

    private fun until(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && !cond()) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
        }
        assertTrue("$what did not happen within 30s", cond())
    }

    private fun writeQaCapture(name: String) {
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/outputs/qa/$name").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 8) for (y in 0 until bmp.height step 8) colors.add(bmp.getPixel(x, y))
        assertTrue("$name is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    private fun covers() = rule.onAllNodes(cover).fetchSemanticsNodes().size
    private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test fun httpErrorShowsRetryNotEmptyAndRetryLoadsTheKeptList() {
        val before = stored()
        assertEquals("b,a", before)
        failing = true
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Watchlist").performClick()
        until("the load-failure message") { shown("Couldn't load your watchlist") }
        writeQaCapture("mystuff-watchlist-load-failure.png")
        rule.onNodeWithText("Retry").assertIsDisplayed()
        assertTrue("an HTTP error must not read as an empty watchlist", !shown("Your watchlist is empty"))
        assertEquals(0, covers())
        assertEquals(listOf("b", "a"), Watchlist.ids.value)
        assertEquals(before, stored())

        failing = false
        rule.onNodeWithText("Retry").performClick()
        until("two covers after Retry") { covers() == 2 }
        writeQaCapture("mystuff-watchlist-retry-recovered.png")
        assertTrue(!shown("Couldn't load your watchlist"))
        assertEquals(before, stored())
    }

    @Test fun shownTitlesStayWhenALaterLoadFails() {
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Watchlist").performClick()
        until("two covers") { covers() == 2 }
        failing = true
        Watchlist.toggle("c") // the user stars another title: a reload runs and now fails
        until("the load-failure message") { shown("Couldn't load your watchlist") }
        assertEquals("titles already on screen stay", 2, covers())
        assertEquals(listOf("c", "b", "a"), Watchlist.ids.value)
        assertEquals("c,b,a", stored())
    }

    @Test fun healthyEmptyAnswerWithSavedIdsSaysTitlesUnavailableNotEmpty() {
        emptyAnswer = true
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Watchlist").performClick()
        until("the unavailable wording") { shown("saved titles aren't available") }
        writeQaCapture("mystuff-watchlist-saved-unavailable.png")
        assertTrue("saved ids exist, so it must not say the watchlist is empty", !shown("Your watchlist is empty"))
        assertEquals(0, covers())
        assertEquals(listOf("b", "a"), Watchlist.ids.value)
        assertEquals("b,a", stored())
    }

    @Test fun anotherAccountWithTheSameIdsNeverSeesTheFirstAccountsTitles() {
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Watchlist").performClick()
        until("two covers for the first account") { covers() == 2 }
        failing = true
        rule.runOnUiThread {
            SessionManager.signIn(Session(url, "u2", "Other", "t2", Graph.tokens.deviceId()))
            Watchlist.toggle("a"); Watchlist.toggle("b") // the second account holds the very same ids
        }
        until("the load-failure message for the second account") { shown("Couldn't load your watchlist") }
        assertEquals("the first account's titles must be gone", 0, covers())
        assertEquals(listOf("b", "a"), Watchlist.ids.value)
        // the first account's stored list is untouched
        Watchlist.setUser(url, "u")
        assertEquals(listOf("b", "a"), Watchlist.ids.value)
        assertEquals("b,a", stored())
    }

    @After fun tearDown() { runCatching { server.shutdown() }; Graph.signOut() }
}
