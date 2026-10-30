package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.Role
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
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.SearchScreen
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Search: recommended rows when blank, typed search hits the server, results render,
 *  and "No matches." never flashes while a search is still in flight. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSearchInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    @Volatile private var searchDelayMs = 0L

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met within ${timeoutMs}ms\nREQUESTED:\n" + requested.joinToString("\n"), condition())
    }

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        searchDelayMs = 0
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += path
                if (path.contains("/Images/")) {
                    val art = if (path.contains("/Items/s1/")) TestArt.poster(7, "Silent Meridian", "2007") else TestArt.poster(1, "Copper Skies", "2005")
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(art))
                }
                if (path.contains("searchTerm=")) {
                    val delay = searchDelayMs
                    if (delay > 0) Thread.sleep(delay)
                    if (path.contains("searchTerm=copper")) {
                        return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"}}],"TotalRecordCount":1}""")
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}""")
                }
                val body = when {
                    path.contains("Items/Suggestions") -> """{"Items":[],"TotalRecordCount":0}"""
                    path.contains("includeItemTypes=Series") -> """{"Items":[{"Id":"s1","Name":"The Silent Meridian","Type":"Series","ProductionYear":2007,"ImageTags":{"Primary":"p7"}}],"TotalRecordCount":1}"""
                    path.contains("/Items") -> """{"Items":[{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"}}],"TotalRecordCount":1}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun blankShowsRecommendedRows() {
        rule.setContent { DriftfinTheme { SearchScreen(onOpen = {}) } }
        awaitReal(20_000) { rule.onAllNodesWithText("Recommended movies").fetchSemanticsNodes().isNotEmpty() }
        awaitReal(20_000) { rule.onAllNodesWithText("Recommended shows").fetchSemanticsNodes().isNotEmpty() }
        // Type isolation: the movies row queried only movies, the shows row only series.
        assertTrue(requested.any { it.contains("sortBy=CommunityRating") && it.contains("includeItemTypes=Movie") })
        assertTrue(requested.any { it.contains("sortBy=CommunityRating") && it.contains("includeItemTypes=Series") })
        assertTrue(requested.none { it.contains("sortBy=CommunityRating") && it.contains("includeItemTypes=Movie,Series") })
        capture("search-recommended.png")
    }

    @Test fun noMatchesNeverFlashesDuringSearch() {
        searchDelayMs = 3_000 // server answers 3s after the request
        rule.setContent { DriftfinTheme { SearchScreen(onOpen = {}) } }
        awaitReal(20_000) { rule.onAllNodesWithText("Recommended movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Search movies and shows").performTextInput("copper")
        rule.mainClock.advanceTimeBy(500)
        // While the request is in flight, "No matches." must not appear.
        val end = System.currentTimeMillis() + 1_800
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(100)
            assertTrue("'No matches.' flashed during an in-flight search", rule.onAllNodesWithText("No matches.").fetchSemanticsNodes().isEmpty())
        }
        awaitReal(20_000) { requested.any { it.contains("searchTerm=copper") } }
        val cover = hasClickAction() and !androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Button)
        awaitReal(20_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        val settleEnd = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < settleEnd) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(60) }
        capture("search-results.png")
    }

    @Test fun clearButtonEmptiesTheBox() {
        rule.setContent { DriftfinTheme { SearchScreen(onOpen = {}) } }
        awaitReal(20_000) { rule.onAllNodesWithText("Recommended movies").fetchSemanticsNodes().isNotEmpty() }
        // No X while the box is empty.
        assertTrue(rule.onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Search movies and shows").performTextInput("copper")
        rule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
        capture("search-clear.png")
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.waitForIdle()
        // The box is empty (placeholder back), the X is gone, and the recommended rows return.
        rule.onNodeWithText("Search movies and shows").assertIsDisplayed()
        awaitReal(10_000) { rule.onAllNodesWithText("Recommended movies").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithContentDescription("Clear search").fetchSemanticsNodes().isEmpty())
    }

    @Test fun noMatchesShowsAfterEmptyResult() {
        rule.setContent { DriftfinTheme { SearchScreen(onOpen = {}) } }
        awaitReal(20_000) { rule.onAllNodesWithText("Recommended movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Search movies and shows").performTextInput("zzzznothing")
        rule.onNodeWithText("zzzznothing").assertExists() // the field accepted the input
        rule.mainClock.advanceTimeBy(500) // push the compose clock past the 300ms debounce
        awaitReal(20_000) { rule.onAllNodesWithText("No matches.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("No matches.").assertIsDisplayed()
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
