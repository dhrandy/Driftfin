package tv.jetflix.phone

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.BrowseScreen
import tv.jetflix.phone.ui.DriftfinTheme

/** Reproduction for "select a movie and hit Back, the list is back at the top". The real Movies list inside a real
 *  NavHost: scroll down, open a title (a stand-in details screen), pop back, and compare the scroll position.
 *  Written to FAIL while the bug exists and pass once the position survives. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneBackKeepsListPositionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var nav: NavHostController

    private fun hasButtonRole() = androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val cover = hasClickAction() and !hasButtonRole()

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        ctx.getSharedPreferences("driftfin_phone_browse", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val items = (0 until 90).joinToString(",") { """{"Id":"m$it","Name":"Movie $it","Type":"Movie"}""" }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.contains("/Images/")) return MockResponse().setResponseCode(404)
                val body = if (path.contains("/Items") && !path.contains("/Filters")) """{"Items":[$items],"TotalRecordCount":90}""" else "{}"
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun until(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && !cond()) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
        }
        assertTrue("$what did not happen within 30s", cond())
    }

    private fun covers() = rule.onAllNodes(cover).fetchSemanticsNodes().size
    private fun scrolled(): Float = rule.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun backFromAMovieKeepsTheMoviesListPosition() {
        rule.setContent {
            DriftfinTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = "list") {
                    composable("list") { BrowseScreen("Movie", "All movies", onOpen = { nav.navigate("detail") }) }
                    composable("detail") { Text("DETAIL SCREEN") }
                }
            }
        }
        until("movie covers") { covers() >= 1 }
        rule.onNode(hasScrollAction()).performScrollToIndex(40)
        rule.waitForIdle()
        val before = scrolled()
        assertTrue("the list must be scrolled before opening a title (was $before)", before > 0f)

        rule.onAllNodes(cover)[0].performClick()
        until("the details stand-in") { rule.onAllNodes(hasText("DETAIL SCREEN")).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { nav.popBackStack() }
        until("covers after Back") { covers() >= 1 }
        rule.waitForIdle()
        val after = scrolled()
        assertTrue("Back must return to the same place: was $before, now $after", after > before / 2f)
    }

    @After fun tearDown() { runCatching { server.shutdown() }; Graph.signOut() }
}
