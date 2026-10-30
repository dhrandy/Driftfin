package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.MyStuffScreen
import java.util.concurrent.CopyOnWriteArrayList

/** My Stuff sorting: the menu re-queries the server with the picked order and the choice persists per section. One flow, because composition state leaks between test methods in a class. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneMyStuffSortTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        ctx.getSharedPreferences("driftfin_phone_browse", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += "${request.method} $path"
                if (path.contains("/Images/")) return MockResponse().setResponseCode(404)
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    path.contains("filters=IsFavorite") -> json.setBody("""{"Items":[{"Id":"f1","Name":"A Favorite","Type":"Movie","ImageTags":{"Primary":"a"}}],"TotalRecordCount":1}""")
                    path.contains("/Items") -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                    else -> json.setBody("""{}""")
                }
            }
        }
        server.start()
        SessionManager.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun sortPicksOrderAndPersistsPerSection() {
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("My stuff").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sort").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sort").performClick()
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Year").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Year").performClick()
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        // The pick re-queries favorites with the chosen server order.
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end && requested.none { it.contains("filters=IsFavorite") && it.contains("sortBy=ProductionYear") }) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(80)
        }
        assertTrue("favorites re-queried with the picked order\nREQUESTED:\n" + requested.joinToString("\n"),
            requested.any { it.contains("filters=IsFavorite") && it.contains("sortBy=ProductionYear") && it.contains("sortOrder=Descending") })
        // Watchlist keeps its own default: its menu shows "Recently added" (star recency) checked, not Year.
        rule.onNodeWithText("Watchlist").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Sort").performClick()
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("✓ Recently added").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("✓ Recently added").assertIsDisplayed()
        rule.onNodeWithText("✓ Recently added").performClick() // dismiss
        rule.waitForIdle()
        // Favorites keeps its own pick: the choice survived the section switch.
        rule.onNodeWithText("Favorites").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Sort").performClick()
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("✓ Year").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("✓ Year").assertIsDisplayed()
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
