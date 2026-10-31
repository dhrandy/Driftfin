package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Browse against a seeded fake server: twelve long-titled movies with real generated artwork,
 *  the library filter round-trips a real pick, taps open titles, and pixels are captured. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneBrowseInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()

    private fun hasButtonRole() = androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Button)

    private val titles = listOf(
        "The Lighthouse at the End of the World", "A Very Long Movie Title That Should Wrap Neatly",
        "Echoes of Tomorrow", "The Last Cartographer", "Beneath Copper Skies", "Winter's Long Shadow",
        "The Silent Meridian", "Paper Boats on Black Water", "Chasing the Aurora", "The Ninth Orchard",
        "Songs for a Rainy City", "The Cartographer's Daughter Returns",
    )


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
                if (path.contains("/Images/")) {
                    val i = Regex("/Items/m(\\d+)/").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(i, titles[i], (2001 + i).toString())))
                }
                val body = when {
                    path.contains("Items/Filters") -> """{"Genres":["Drama","Sci-Fi","Science Fiction","Made-for-Television"]}"""
                    path.contains("UserViews") -> """{"Items":[{"Id":"libM","Name":"Movies","CollectionType":"movies"},{"Id":"libK","Name":"Kids"}]}"""
                    path.contains("/Items") -> "{\"Items\":[" + titles.mapIndexed { i, t ->
                        """{"Id":"m$i","Name":"$t","Type":"Movie","ProductionYear":${2001 + i},"ImageTags":{"Primary":"p$i"}}"""
                    }.joinToString(",") + "],\"TotalRecordCount\":12}"
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    /** Lets Coil fetch artwork, then holds a steady frame. */
    private fun settle(ms: Long = 6000) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(60) }
    }

    private fun awaitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(80)
        }
        val prefsNow = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("driftfin_phone_browse", 0).all.toString()
        java.io.File("build/render-check/debug-awaitReal-" + System.currentTimeMillis() + ".txt").writeText("PREFS: " + prefsNow + "\nREQUESTED:\n" + requested.joinToString("\n"))
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("$name is not a blank frame (${colors.size} colors)", colors.size > 30)
    }

    @Test fun seededGridRendersArtAndTapOpens() {
        var opened: BaseItemDto? = null
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "Movies", onOpen = { opened = it }) } }
        rule.onNodeWithText("Movies").assertIsDisplayed()
        val cover = hasClickAction() and !hasButtonRole()
        rule.waitUntil(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().size >= 4 }
        settle()
        capture("browse-seeded.png")
        // Real artwork must reach the pixels: the cover region carries more than the flat surface color.
        val decor = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val region = HashSet<Int>()
        for (x in 40 until 320 step 4) for (y in 900 until 1300 step 4) region.add(bmp.getPixel(x, y))
        assertTrue("cover pixels show fetched artwork (${region.size} colors)", region.size > 8)
        rule.onAllNodes(cover)[0].performClick()
        rule.waitForIdle()
        assertEquals("m0", opened?.Id)
    }

    @Test fun sortIconOpensMenu() {
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "Movies", onOpen = {}) } }
        rule.onNodeWithText("Movies").assertIsDisplayed()
        awaitReal(30_000) { rule.onAllNodesWithContentDescription("Sort and filter").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Sort and filter").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Recently added", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Name", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Rating", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Year", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Unwatched only", substring = true).assertIsDisplayed()
        capture("browse-sort-menu-icon.png")
    }

    @Test fun libraryFilterSendsPickedLibrary() {
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "Movies", onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodesWithText("Library: All").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Library: All").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Kids").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Kids").performClick()
        awaitReal(30_000) { rule.onAllNodesWithText("Library: Kids").fetchSemanticsNodes().isNotEmpty() }
        awaitReal(30_000) { requested.any { it.contains("/Items") && it.contains("parentId=libK") } }
        // Dispatch threads race, so a slow unscoped load can record after the reload; the stable
        // guarantee is that the latest library-scoped query uses the picked library.
        assertTrue(requested.lastOrNull { it.contains("/Items?") && it.contains("parentId=") }!!.contains("parentId=libK"))
    }


    @Test fun controlsWrapWithinWidthWithLongPicks() {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        rule.setContent { DriftfinTheme { BrowseScreen("Movie", "Movies", onOpen = {}) } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Genres").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Genres").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Science Fiction").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Science Fiction").performClick()
        rule.onNodeWithText("Made-for-Television").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Science Fiction, Made-for-Television").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Science Fiction, Made-for-Television").performClick() // close the menu
        assertControlsWithinWidth()
        settle(2500)
        capture("browse-controls-wrap.png")
    }

    @Test fun controlsWrapAtNarrowWidths() {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("driftfin_phone_browse", 0).edit().clear().commit()
        rule.setContent {
            DriftfinTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredWidth(360.dp).height(430.dp)) { BrowseScreen("Movie", "Movies", onOpen = {}) }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredWidth(320.dp).height(430.dp)) { BrowseScreen("Movie", "Movies", onOpen = {}) }
                }
            }
        }
        awaitReal(30_000) { rule.onAllNodesWithContentDescription("Sort and filter").fetchSemanticsNodes().size >= 2 }
        val d = rule.activity.resources.displayMetrics.density
        val controls = rule.onAllNodesWithTag("browseControls").fetchSemanticsNodes()
        val sorts = rule.onAllNodesWithContentDescription("Sort and filter").fetchSemanticsNodes()
        assertTrue("expected two BrowseScreens, got ${controls.size} controls rows", controls.size == 2)
        for ((i, widthDp) in listOf(360, 320).withIndex()) {
            val limit = widthDp * d
            val cb = controls[i].boundsInRoot
            assertTrue("controls row overflows ${widthDp}dp (right=${cb.right} of $limit)", cb.right <= limit + 1f)
            val b = sorts[i].boundsInRoot
            assertTrue("Sort - Filter overflows ${widthDp}dp (right=${b.right} of $limit)", b.right <= limit + 1f)
        }
        settle(2000)
        capture("browse-controls-wrap-narrow.png")
    }

    private fun assertControlsWithinWidth() {
        val screenW = rule.activity.resources.displayMetrics.widthPixels.toFloat()
        val cb = rule.onNodeWithTag("browseControls").fetchSemanticsNode().boundsInRoot
        assertTrue("controls row runs off-screen (right=${cb.right} of $screenW)", cb.right <= screenW + 1f)
        assertTrue("controls row starts off-screen (left=${cb.left})", cb.left >= -1f)
        val library = rule.onNodeWithText("Library: All").fetchSemanticsNode().boundsInRoot
        assertTrue("Library: All runs off-screen (right=${library.right} of $screenW)", library.right <= screenW + 1f)
        assertTrue("Library: All starts off-screen (left=${library.left})", library.left >= -1f)
        val sortB = rule.onNodeWithContentDescription("Sort and filter").fetchSemanticsNode().boundsInRoot
        assertTrue("sort control runs off-screen (right=${sortB.right} of $screenW)", sortB.right <= screenW + 1f)
        assertTrue("sort control starts off-screen (left=${sortB.left})", sortB.left >= -1f)
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
