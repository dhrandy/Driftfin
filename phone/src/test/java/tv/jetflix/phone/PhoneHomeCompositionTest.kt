package tv.jetflix.phone

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performScrollToNode
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
import tv.jetflix.phone.ui.HomeScreen

/** Home's composition, matching the TV app: Continue Watching, Next Up, the two combined Recently
 *  Added rows (both movie libraries together, both show libraries together), Favorites, and
 *  "More like <last watched>" last. There are no per-library rows and no "Libraries" card row:
 *  the combined rows already cover every library, and full libraries live in Browse. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneHomeCompositionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer

    /** What the last-played fetch answers; empty means nothing was ever played. */
    var playedJson = """{"Items":[{"Id":"p1","Name":"Drama Film","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}}],"TotalRecordCount":1}"""
    /** What the similar-items fetch answers. */
    var similarJson = """{"Items":[
        {"Id":"w1","Name":"Similar One","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}},
        {"Id":"w2","Name":"Similar Two","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}},
        {"Id":"w3","Name":"Similar Three","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}},
        {"Id":"w4","Name":"Similar Four","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}}],"TotalRecordCount":4}"""

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
                // Four video libraries exist on this server; Home must still show no per-library rows.
                if (path.contains("/UserViews")) return json("""{"Items":[
                    {"Id":"lib2","Name":"TV Shows","CollectionType":"tvshows"},
                    {"Id":"lib4","Name":"Kids TV Shows","CollectionType":"tvshows"},
                    {"Id":"lib1","Name":"Movies","CollectionType":"movies"},
                    {"Id":"lib3","Name":"Kids Movies","CollectionType":"movies"},
                    {"Id":"lib5","Name":"Music","CollectionType":"music"}],"TotalRecordCount":5}""".replace("\\s+".toRegex(), " "))
                if (path.contains("/UserItems/Resume")) return json("""{"Items":[${item("c1", "Resume Film", "Movie")}],"TotalRecordCount":1}""")
                if (path.contains("/Shows/NextUp")) return json("""{"Items":[${item("n1", "Next Episode", "Episode")}],"TotalRecordCount":1}""")
                // Items/Latest returns a bare array (see JellyfinApi.getLatest), not an Items wrapper.
                if (path.contains("/Items/Latest")) {
                    return if (url.queryParameter("includeItemTypes") == "Episode")
                        json("""[${item("e1", "Fresh Episode", "Episode")}]""")
                    else json("""[${item("m1", "Fresh Film", "Movie")}]""")
                }
                if (path.contains("/Similar")) return json(similarJson)
                if (path.endsWith("/Items") && url.queryParameter("filters") == "IsPlayed") return json(playedJson)
                if (path.endsWith("/Items") && url.queryParameter("filters") == "IsFavorite") return json("""{"Items":[${item("f1", "Fave Film", "Movie")}],"TotalRecordCount":1}""")
                return json("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun showHome() { rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } } }

    /** Home's LazyColumn is the one tall vertical lazy list (section rows are short horizontal ones). */
    private fun homeList() = hasScrollToIndexAction() and SemanticsMatcher("home list") { it.boundsInRoot.height > 1000f }

    @Test fun homeRowsMatchTvCompositionAndOrder() {
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Continue Watching").fetchSemanticsNodes().isNotEmpty() }
        assertStrictOrder(listOf("Continue Watching", "Next Up", "Recently Added Movies", "Recently Added Episodes", "Favorites", "More like Drama Film"))
    }

    @Test fun noPerLibraryRowsOrLibrariesCard() {
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Recently Added Movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(homeList()).performScrollToNode(hasText("Favorites"))
        rule.onNodeWithText("Favorites").assertIsDisplayed()
        // Libraries exist on the server (see UserViews above), but none gets its own Home row.
        listOf("Libraries", "Movies", "TV Shows", "Kids Movies", "Kids TV Shows", "Music", "Recommended").forEach {
            assertTrue("$it must not appear on Home", rule.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun moreLikeHiddenWhenTooFewMatches() {
        similarJson = """{"Items":[{"Id":"w1","Name":"Similar One","Type":"Movie","Genres":["Drama"],"ImageTags":{"Primary":"x"}}],"TotalRecordCount":1}"""
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Recently Added Movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(homeList()).performScrollToNode(hasText("Favorites"))
        rule.onNodeWithText("Favorites").assertIsDisplayed()
        rule.onNode(homeList()).performScrollToNode(hasText("Favorites"))
        assertTrue(rule.onAllNodesWithText("More like Drama Film", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun moreLikeHiddenWhenNothingPlayed() {
        playedJson = """{"Items":[],"TotalRecordCount":0}"""
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Recently Added Movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(homeList()).performScrollToNode(hasText("Favorites"))
        rule.onNodeWithText("Favorites").assertIsDisplayed()
        rule.onNode(homeList()).performScrollToNode(hasText("Favorites"))
        assertTrue(rule.onAllNodesWithText("More like", substring = true).fetchSemanticsNodes().isEmpty())
    }

    /** Strict positional proof that never reads a disposed node: walk the expected order, scrolling
     *  each heading into view. The list's scroll offset must never decrease (a wrong order forces a
     *  backward scroll = fail); when a step needs no scrolling the viewport did not move, so the
     *  previous heading is still composed and on-screen tops decide. */
    private fun assertStrictOrder(expected: List<String>) {
        val listOffset = {
            rule.onNode(homeList()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value.invoke()
        }
        var lastOffset = -1f
        var previous: String? = null
        expected.forEach { name ->
            rule.onNode(homeList()).performScrollToNode(hasText(name))
            rule.onNodeWithText(name).assertIsDisplayed()
            val offset = listOffset()
            val prev = previous
            if (prev != null) {
                if (offset == lastOffset) {
                    val prevTop = rule.onNodeWithText(prev).getBoundsInRoot().top
                    val top = rule.onNodeWithText(name).getBoundsInRoot().top
                    assertTrue("$prev must sit above $name (prevTop=$prevTop, top=$top)", prevTop < top)
                }
            }
            assertTrue("$name must not require scrolling back up (offset=$offset, previous=$lastOffset)", offset >= lastOffset)
            lastOffset = offset
            previous = name
        }
    }
}
