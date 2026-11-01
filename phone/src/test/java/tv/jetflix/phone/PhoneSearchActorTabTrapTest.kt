package tv.jetflix.phone

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Search pushes actor pages with a plain nav.navigate on top of the Search tab. Leaving such a page
 * through a bottom tab must drop the whole Search subtree: a saved-state restore must never bring
 * Search (or the actor page) back instead of the tapped tab. Covers the actor-above-Search path of
 * the tab trap fixed in goTo(); PhoneShellGoToTest covers the same path at the graph level.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSearchActorTabTrapTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        // Let Compose's own idling drive the clock (waitUntil), same pattern as PhoneSearchOtherTabTest.
        try { compose.waitUntil(timeoutMs) { condition() }; return } catch (e: Throwable) { requested += "WAITUNTIL threw: " + e.javaClass.simpleName + " " + (e.message ?: "").take(160) }
        val tree = try {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            val n = roots.fetchSemanticsNodes().size
            val t = (0 until n).joinToString("\n--- ROOT %d ---\n") { i -> roots[i].printToString() }
            if (t.length > 12000) t.take(12000) + "\n...[truncated]" else t
        } catch (e: Exception) { "tree dump failed: $e" }
        assertTrue("condition not met within ${timeoutMs}ms\nREQUESTED:\n" + requested.joinToString("\n") + "\nSEMANTICS:\n" + tree, condition())
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
                if (path.contains("/Images/")) {
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "Copper Skies", "2005")))
                }
                if (path.startsWith("/Persons")) {
                    // The actor search: one synthetic person for a typed term, none otherwise.
                    val body = if (path.contains("searchTerm"))
                        """{"Items":[{"Id":"a1","Name":"Mira Voss","Type":"Person","ImageTags":{}}],"TotalRecordCount":1}"""
                    else
                        """{"Items":[],"TotalRecordCount":0}"""
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
                val body = when {
                    path.contains("Items/Suggestions") -> """{"Items":[],"TotalRecordCount":0}"""
                    path.contains("includeItemTypes=Series") && path.contains("limit=40") -> """{"Items":[{"Id":"s1","Name":"The Silent Meridian","Type":"Series","ProductionYear":2007,"ImageTags":{"Primary":"p7"}}],"TotalRecordCount":1}"""
                    path.contains("/Items") -> """{"Items":[{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"}}],"TotalRecordCount":1}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
        // Fresh installs default last_seen_build to 0, which pops the What's New AlertDialog (a second
        // Compose root) over every test. Dismiss it permanently for this run.
        tv.jetflix.phone.data.PhoneSettings.setLastSeenBuild(BuildConfig.VERSION_CODE)
    }

    // Every test starts MainActivity (Home refresh loop and all). Destroy it so its Compose root and loops do not outlive the test in the shared JVM.
    private var mainController: org.robolectric.android.controller.ActivityController<MainActivity>? = null
    private fun launchMain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        mainController = controller // tracked before setup(), so a partial setup is still cleaned up
        controller.setup()
    }

    @After fun tearDown() {
        try { mainController?.destroy() } finally { mainController = null; server.shutdown() }
    }

    private fun assertSelectedTab(label: String) {
        awaitReal(30_000) {
            compose.onAllNodes(isSelected().and(hasText(label)), useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Search tab -> type a term -> tap the person in the Actors row -> actor page over Search. */
    private fun openActorAboveSearch() {
        launchMain()
        awaitReal(30_000) { compose.onAllNodesWithText("Search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search").performClick()
        awaitReal(30_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("mira")
        awaitReal(30_000) { compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mira Voss").performClick()
        // Actor page pushed over Search: the search field leaves the tree, the person header stays.
        awaitReal(30_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        awaitReal(30_000) { compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun homeTabFromActorAboveSearchLandsOnHome() {
        openActorAboveSearch()
        compose.onNodeWithText("Home").performClick()
        assertSelectedTab("Home")
        awaitReal(30_000) { compose.onAllNodesWithText("DRIFTFIN").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(
            "actor page must be gone after the Home tap\nREQUESTED:\n" + requested.joinToString("\n"),
            compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test fun myStuffTabFromActorAboveSearchLandsOnMyStuff() {
        openActorAboveSearch()
        compose.onNodeWithText("My stuff").performClick()
        assertSelectedTab("My stuff")
        awaitReal(30_000) { compose.onAllNodesWithText("Favorites").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(
            "actor page must be gone after the My stuff tap\nREQUESTED:\n" + requested.joinToString("\n"),
            compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isEmpty(),
        )
    }
}
