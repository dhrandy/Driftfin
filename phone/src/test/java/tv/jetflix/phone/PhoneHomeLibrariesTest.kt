package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.printToLog
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen

/** Home's library shortcuts: every video library the account can see, under its own name, opening
 *  its own browse page; a Recommended row sits below them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneHomeLibrariesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    var viewsJson = """{"Items":[
        {"Id":"lib1","Name":"Movies","CollectionType":"movies"},
        {"Id":"lib2","Name":"TV Shows","CollectionType":"tvshows"},
        {"Id":"lib3","Name":"Kids Movies","CollectionType":"movies"},
        {"Id":"lib4","Name":"Kids TV Shows","CollectionType":"tvshows"}],"TotalRecordCount":4}"""

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
                if (path.contains("/Images/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "X", "2000")))
                if (path.contains("/UserViews")) return MockResponse().setHeader("Content-Type", "application/json").setBody(viewsJson.replace("\\s+".toRegex(), " "))
                val body = when {
                    path.contains("/Items/Suggestions") && path.contains("type=Movie") -> """{"Items":[{"Id":"rm1","Name":"Suggested Movie","Type":"Movie","CommunityRating":8.5,"ImageTags":{"Primary":"x"}}],"TotalRecordCount":1}"""
                    path.contains("/Items/Suggestions") && path.contains("type=Series") -> """{"Items":[{"Id":"rs1","Name":"Suggested Show","Type":"Series","CommunityRating":7.5,"ImageTags":{"Primary":"x"}}],"TotalRecordCount":1}"""
                    else -> """{"Items":[],"TotalRecordCount":0}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun showHome(tapSink: (BaseItemDto) -> Unit) {
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}, onOpenLibrary = tapSink) } }
    }

    @Test fun libraryShortcutsAndRecommendedRow() {
        var tapped: BaseItemDto? = null
        showHome { tapped = it }
        awaitReal(30_000) { rule.onAllNodesWithText("Libraries").fetchSemanticsNodes().isNotEmpty() }
        names().forEach { scrollTileTo(it); rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Recommended").assertExists()
        scrollTileTo("Kids Movies")
        rule.onNodeWithText("Kids Movies").performClick()
        awaitReal(10_000) { tapped?.Id == "lib3" }
        assertEquals("Kids Movies", tapped?.Name)
    }

    @Test fun renamedAndDuplicateKindLibrariesKeepTheirOwnNames() {
        viewsJson = """{"Items":[
            {"Id":"v1","Name":"Films","CollectionType":"movies"},
            {"Id":"v2","Name":"Series","CollectionType":"tvshows"},
            {"Id":"v3","Name":"Family","CollectionType":"movies"},
            {"Id":"v4","Name":"Documentaries","CollectionType":"movies"}],"TotalRecordCount":4}"""
        showHome {}
        awaitReal(30_000) { rule.onAllNodesWithText("Libraries").fetchSemanticsNodes().isNotEmpty() }
        // Names come straight from the server: no fixed labels, and several of the same kind all show.
        listOf("Films", "Series", "Family", "Documentaries").forEach {
            scrollTileTo(it); rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    private fun names() = listOf("Movies", "TV Shows", "Kids Movies", "Kids TV Shows")

    private fun scrollTileTo(name: String) {
        val horizontalList = androidx.compose.ui.test.hasScrollAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange)
        rule.onAllNodes(horizontalList).onFirst().performScrollToNode(hasText(name))
    }

    /** A restricted profile's UserViews is already server-scoped: it only ever lists what that
     *  account may see. Home must show exactly that subset - nothing more, no fallback to all. */
    @Test fun restrictedProfileSeesOnlyItsOwnLibraries() {
        viewsJson = """{"Items":[
            {"Id":"k1","Name":"Kids Movies","CollectionType":"movies"},
            {"Id":"k2","Name":"Kids TV Shows","CollectionType":"tvshows"}],"TotalRecordCount":2}"""
        showHome {}
        awaitReal(30_000) { rule.onAllNodesWithText("Libraries").fetchSemanticsNodes().isNotEmpty() }
        scrollTileTo("Kids Movies"); rule.onNodeWithText("Kids Movies").assertIsDisplayed()
        scrollTileTo("Kids TV Shows"); rule.onNodeWithText("Kids TV Shows").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Movies").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("TV Shows").fetchSemanticsNodes().isEmpty())
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
