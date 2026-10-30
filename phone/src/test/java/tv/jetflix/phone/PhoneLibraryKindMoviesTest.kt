package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import tv.jetflix.phone.ui.BrowseScreen
import tv.jetflix.phone.ui.DriftfinTheme
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** The Browse Library picker only offers libraries that fit the tab (reported 10/29: "Shows shouldn't
 *  be under movies", "Movies shouldn't be under shows"). Fixture mirrors his roster. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneLibraryKindMoviesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("condition not met\nREQUESTED:\n" + requested.joinToString("\n"), condition())
    }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        ctx.getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        Graph.rebuildImagesForTest()
        Graph.signOut()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                requested += path
                if (path.contains("/Images/")) return MockResponse().setResponseCode(404)
                val body = when {
                    path.contains("Items/Filters") -> """{"Genres":["Drama"]}"""
                    path.contains("UserViews") -> """{"Items":[""" +
                        """{"Id":"libM","Name":"Movies","CollectionType":"movies"},""" +
                        """{"Id":"libS","Name":"Shows","CollectionType":"tvshows"},""" +
                        """{"Id":"libKM","Name":"Kids Movies","CollectionType":"movies"},""" +
                        """{"Id":"libKS","Name":"Kids Shows","CollectionType":"tvshows"},""" +
                        """{"Id":"libR","Name":"Recordings"}]}"""
                    path.contains("/Items") && path.contains("includeItemTypes=Movie") -> """{"Items":[{"Id":"m1","Name":"Some Movie","Type":"Movie","ImageTags":{"Primary":"a"}}],"TotalRecordCount":1}"""
                    path.contains("/Items") && path.contains("includeItemTypes=Series") -> """{"Items":[{"Id":"s1","Name":"Some Show","Type":"Series","ImageTags":{"Primary":"b"}}],"TotalRecordCount":1}"""
                    path.contains("/Items") -> """{"Items":[],"TotalRecordCount":0}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun moviesTabOffersOnlyMovieLibraries() {
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "All movies", onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodesWithText("Library: All").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Library: All").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Kids Movies").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Movies").assertIsDisplayed()
        rule.onNodeWithText("Kids Movies").assertIsDisplayed()
        rule.onNodeWithText("Recordings").assertIsDisplayed() // no declared kind: fits both tabs
        assertTrue("Shows must not be offered under Movies", rule.onAllNodesWithText("Shows").fetchSemanticsNodes().isEmpty())
        assertTrue("Kids Shows must not be offered under Movies", rule.onAllNodesWithText("Kids Shows").fetchSemanticsNodes().isEmpty())
        capturePopup("browse-library-kind-movies.png")
    }

    /** The menu is a popup, not in the activity decorView: draw the last-added window view. */
    private fun capturePopup(name: String) {
        Thread.sleep(800); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val wmgClass = Class.forName("android.view.WindowManagerGlobal")
        val wmg = wmgClass.getMethod("getInstance").invoke(null)
        val viewsField = wmgClass.getDeclaredField("mViews"); viewsField.isAccessible = true
        @Suppress("UNCHECKED_CAST") val windowViews = viewsField.get(wmg) as ArrayList<android.view.View>
        val popupView = windowViews.last()
        popupView.measure(android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.AT_MOST), android.view.View.MeasureSpec.makeMeasureSpec(2000, android.view.View.MeasureSpec.AT_MOST))
        popupView.layout(0, 0, popupView.measuredWidth, popupView.measuredHeight)
        val bmp = Bitmap.createBitmap(popupView.measuredWidth, popupView.measuredHeight, Bitmap.Config.ARGB_8888)
        popupView.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
