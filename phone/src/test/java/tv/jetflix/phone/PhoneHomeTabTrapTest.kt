package tv.jetflix.phone

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
 * The Home-tab twin of the Search trap: with no tab tap ever made, Home -> details -> cast actor is a
 * stack of directly pushed pages above Home. Tapping Home must land on Home; a saved-state restore must
 * never resurrect the pushed pages instead. PhoneShellGoToTest covers the same paths at the graph level.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneHomeTabTrapTest {
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
                val body = when {
                    // Single-item lookup (details page): one movie carrying a one-person cast.
                    Regex("/Items/[^/?]+\\?userId=.*").matches(path) ->
                        """{"Id":"m4","Name":"Beneath Copper Skies","Type":"Movie","ProductionYear":2005,"ImageTags":{"Primary":"p4"},"People":[{"Id":"a1","Name":"Mira Voss","Type":"Actor"}]}"""
                    path.startsWith("/Persons") -> """{"Items":[],"TotalRecordCount":0}"""
                    path.contains("Items/Suggestions") -> """{"Items":[],"TotalRecordCount":0}"""
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

    @After fun tearDown() { server.shutdown() }

    private fun assertSelectedTab(label: String) {
        awaitReal(30_000) {
            compose.onAllNodes(isSelected().and(hasText(label)), useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun homeTabFromDetailsActorStackLandsOnHome() {
        Robolectric.buildActivity(MainActivity::class.java).setup()
        // Home hero -> More info -> details page (bar hidden), then its cast -> actor page (bar visible).
        awaitReal(30_000) { compose.onAllNodesWithText("More info").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("More info").performClick()
        awaitReal(30_000) { compose.onAllNodesWithText("Cast").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToNode(hasText("Mira Voss"))
        compose.onNodeWithText("Mira Voss").performClick()
        awaitReal(30_000) { compose.onAllNodesWithText("Cast").fetchSemanticsNodes().isEmpty() }
        awaitReal(30_000) { compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isNotEmpty() }
        // The trap: tapping Home used to resurrect the pushed pages. It must land on Home and stay.
        compose.onNodeWithText("Home").performClick()
        assertSelectedTab("Home")
        awaitReal(30_000) { compose.onAllNodesWithText("DRIFTFIN").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(
            "actor/details pages must be gone after the Home tap\nREQUESTED:\n" + requested.joinToString("\n"),
            compose.onAllNodesWithText("Mira Voss").fetchSemanticsNodes().isEmpty(),
        )
    }
}
