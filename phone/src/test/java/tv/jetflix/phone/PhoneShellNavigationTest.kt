package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
import tv.jetflix.phone.ui.DriftfinShell
import tv.jetflix.phone.ui.DriftfinTheme
import java.io.File

/** The real shell: bottom navigation actually moves between tabs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneShellNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = java.util.concurrent.CopyOnWriteArrayList<String>()

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
                val path = request.path ?: ""
                requested += path
                if (path.contains("searchTerm=copper")) {
                    Thread.sleep(800) // slow response, so the tab switch below happens mid-flight
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"}}],"TotalRecordCount":1}""")
                }
                val body = when {
                    path.contains("Suggestions") || path.contains("/Items") -> """{"Items":[],"TotalRecordCount":0}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun bottomBarNavigatesBetweenTabs() {
        rule.setContent { DriftfinTheme { DriftfinShell() } }
        rule.waitForIdle()
        rule.onNodeWithText("My stuff").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Watchlist").assertIsDisplayed() // My Stuff section chips appear
        rule.onNodeWithText("Search").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Search movies, shows and actors").assertIsDisplayed() // Search screen field
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Check for updates").assertIsDisplayed()

        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/render-check"); out.mkdirs()
        java.io.File(out, "shell-settings.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("shell-settings.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    /** Leaving Search and coming back starts it empty, and a response that lands after the tab was
     *  left must not leak its results into the fresh visit. The copper response is delayed 800ms so
     *  the switch away happens mid-flight. */
    @Test fun searchClearsAfterLeavingAndReturning() {
        rule.setContent { DriftfinTheme { DriftfinShell() } }
        rule.waitForIdle()
        rule.onNodeWithText("Search").performClick()
        awaitReal(30_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Search movies, shows and actors")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Search movies, shows and actors").performTextInput("copper")
        rule.mainClock.advanceTimeBy(400) // past the 300ms debounce, deterministically
        // Search-as-typed fires, and the server is still answering when we leave.
        val firedEnd = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < firedEnd && requested.none { it.contains("searchTerm=copper") }) {
            rule.mainClock.advanceTimeBy(200); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
        }
        assertTrue("search never fired. REQUESTS: " + requested.joinToString(" | "), requested.any { it.contains("searchTerm=copper") })
        rule.onNodeWithText("My stuff").performClick()
        awaitReal(30_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Watchlist")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Search").performClick()
        // The box is back to its placeholder: the typed term is gone.
        awaitReal(30_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Search movies, shows and actors")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the old term survived the tab switch", rule.onAllNodes(androidx.compose.ui.test.hasText("copper")).fetchSemanticsNodes().isEmpty())
        // Let the delayed response land. The visit it belonged to is gone; the fresh one must stay empty.
        Thread.sleep(1_500); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // A poster card is clickable but neither a button nor a nav tab; the bottom bar is always clickable.
        val role = androidx.compose.ui.semantics.SemanticsProperties.Role
        val cover = androidx.compose.ui.test.hasClickAction() and
            !androidx.compose.ui.test.SemanticsMatcher.expectValue(role, androidx.compose.ui.semantics.Role.Button) and
            !androidx.compose.ui.test.SemanticsMatcher.expectValue(role, androidx.compose.ui.semantics.Role.Tab) and
            !androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.IsEditable)
        val leaked = rule.onAllNodes(cover).fetchSemanticsNodes()
        assertTrue("stale results leaked into the new Search visit: " + leaked.map { n -> n.config.toString().take(160) }, leaked.isEmpty())
        assertTrue("a second search fired after returning", requested.count { it.contains("searchTerm=") } == 1)
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
