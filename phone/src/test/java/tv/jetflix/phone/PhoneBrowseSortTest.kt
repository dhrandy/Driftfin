package tv.jetflix.phone

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.BrowseScreen
import tv.jetflix.phone.ui.DriftfinTheme
import java.util.concurrent.CopyOnWriteArrayList

/** Movies tab Year sort, end to end: the re-query asks the server for the year order and the grid
 *  leads with the newest movie even when the server answers alphabetically (reported 10/29). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneBrowseSortTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    private var opened: BaseItemDto? = null
    private val cover = hasClickAction() and !SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Button)

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        ctx.getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        Graph.rebuildImagesForTest()
        Graph.signOut()
        opened = null
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += path
                if (path.contains("/Images/")) return MockResponse().setResponseCode(404)
                val body = when {
                    path.contains("Items/Filters") -> """{"Genres":["Drama"]}"""
                    path.contains("UserViews") -> """{"Items":[{"Id":"libM","Name":"Movies","CollectionType":"movies"}]}"""
                    path.contains("/Items") -> """{"Items":[
                        {"Id":"a","Name":"Alpha","Type":"Movie","ProductionYear":2001,"CommunityRating":7.1,"ImageTags":{"Primary":"pa"}},
                        {"Id":"b","Name":"Middle","Type":"Movie","ProductionYear":1999,"CommunityRating":8.2,"ImageTags":{"Primary":"pb"}},
                        {"Id":"c","Name":"Zulu","Type":"Movie","ProductionYear":2024,"CommunityRating":6.3,"ImageTags":{"Primary":"pc"}}],"TotalRecordCount":3}""".replace("\n                        ", "")
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met within ${timeoutMs}ms\nREQUESTED:\n" + requested.joinToString("\n"), condition())
    }

    @Test fun yearSortSurvivesAnAlphabeticalServer() {
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "Movies", onOpen = { opened = it }) } }
        awaitReal(20_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Sort and filter").performClick()
        awaitReal(10_000) { rule.onAllNodesWithText("Year").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Year").performClick()
        // The re-query asks the server for the year order, newest first.
        awaitReal(10_000) { requested.any { it.contains("/Items") && it.contains("sortBy=ProductionYear") && it.contains("sortOrder=Descending") } }
        // The server answered alphabetically (Alpha first); the grid must still lead with 2024's Zulu.
        awaitReal(20_000) { rule.onAllNodes(cover).fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(500)
        rule.onAllNodes(cover)[0].performClick()
        rule.waitForIdle()
        assertEquals("newest movie leads after Year sort", "Zulu", opened?.Name)
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
