package tv.jetflix.phone

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.MyStuffScreen
import java.io.File

/** My Stuff against a fake server: sections render, favorites load, tapping a cover opens it, pixels captured. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneMyStuffInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun hasButtonRole() = androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Button)
    // Covers are the clickable nodes carrying no label; chips carry their section name.
    private val cover = hasClickAction() and !hasButtonRole() and
        !hasText("Favorites") and !hasText("Watchlist") and !hasText("Collections")

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
                if (path.contains("/Images/")) {
                    val id = Regex("/Items/([^/]+)/").find(path)?.groupValues?.get(1) ?: "f1"
                    val title = if (id == "f2") "The Series That Never Seems to End" else "A Favorite With a Properly Long Title"
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(id.hashCode(), title)))
                }
                val body = when {
                    path.contains("filters=IsFavorite") -> """{"Items":[{"Id":"f1","Name":"A Favorite With a Properly Long Title","Type":"Movie","ImageTags":{"Primary":"a"}},{"Id":"f2","Name":"The Series That Never Seems to End","Type":"Series","ImageTags":{"Primary":"b"}}],"TotalRecordCount":2}"""
                    path.contains("/Items") -> """{"Items":[],"TotalRecordCount":0}"""
                    else -> """{}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @Test fun sectionsRenderAndFavoriteTapOpens() {
        var opened: BaseItemDto? = null
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = { opened = it }) } }
        rule.onNodeWithText("My stuff").assertIsDisplayed()
        rule.onNodeWithText("Favorites").assertIsDisplayed()
        rule.onNodeWithText("Watchlist").assertIsDisplayed()
        rule.onNodeWithText("Collections").assertIsDisplayed()
        rule.waitUntil(30_000) { rule.onAllNodes(cover).fetchSemanticsNodes().size >= 2 }
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(60) }

        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "mystuff-seeded.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("mystuff.png is not a blank frame (${colors.size} colors)", colors.size > 3)

        rule.onAllNodes(cover)[0].performClick()
        rule.waitForIdle()
        assertEquals("f1", opened?.Id)
    }


    @Test fun sortHiddenOnCollections() {
        rule.setContent { DriftfinTheme { MyStuffScreen(onOpen = {}) } }
        rule.onNodeWithText("Collections").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithContentDescription("Sort").fetchSemanticsNodes().isEmpty())
    }

    @Test fun watchlistOrderingIsClientSide() {
        fun dto(id: String, name: String, rating: Float?, year: Int?) = BaseItemDto(Id = id, Name = name, Type = "Movie", CommunityRating = rating, ProductionYear = year)
        val found = listOf(dto("a", "Bravo", 7.0f, 2001), dto("b", "Alpha", 9.0f, 1999), dto("c", "Charlie", null, 2020))
        val starred = listOf("c", "a", "b")
        assertEquals(listOf("c", "a", "b"), tv.jetflix.phone.data.Content.orderWatchlist(found, starred, tv.jetflix.phone.data.Sort.Added).map { it.Id })
        assertEquals(listOf("b", "a", "c"), tv.jetflix.phone.data.Content.orderWatchlist(found, starred, tv.jetflix.phone.data.Sort.Name).map { it.Id })
        assertEquals(listOf("b", "a", "c"), tv.jetflix.phone.data.Content.orderWatchlist(found, starred, tv.jetflix.phone.data.Sort.Rating).map { it.Id })
        assertEquals(listOf("c", "a", "b"), tv.jetflix.phone.data.Content.orderWatchlist(found, starred, tv.jetflix.phone.data.Sort.Year).map { it.Id })
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
