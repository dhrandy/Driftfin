package tv.jetflix.phone

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import tv.jetflix.phone.data.PhoneSettings
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen

/** The "Exclude animated movies from Home banner" setting, matching the TV app: with it on an animated
 *  movie never becomes the Home hero, while rows are untouched; off, animated titles can be the hero.
 *
 *  Two fixture decisions, both grounded in the production view hierarchy:
 *  - Title logos are disabled (PhoneSettings.setTitleLogos(false)): TitleOrLogo races text against the
 *    logo image - the mock server answers a valid PNG for every /Images/ request, including Logo URLs
 *    that a real server would 404, so with logos enabled the hero title is text or an image depending
 *    on fetch timing. These tests are about hero filtering, not logo rendering.
 *  - Row membership is proven by the card's poster ART REQUEST, keyed by item id and maxWidth
 *    (hero art = 900/960, row card art = 400): PosterCard draws no title text - the title lives in the
 *    title-poster artwork - so no text node exists to scroll to or assert on a card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneHeroAnimationFilterTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer

    /** What the hero-candidates request answers. */
    var heroJson = """{"Items":[],"TotalRecordCount":0}"""
    /** What the resume request answers. */
    var resumeJson = """{"Items":[],"TotalRecordCount":0}"""

    /** Every image request the UI made, as "path?query" - the witness for which items rendered where. */
    private val requestedImages = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun movie(id: String, name: String, genre: String) =
        """{"Id":"$id","Name":"$name","Type":"Movie","Genres":["$genre"],"CommunityRating":9.0,"BackdropImageTags":["b"],"ImageTags":{"Primary":"x"}}"""

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

    /** Image requests for [id] at hero widths (hero poster 900, top backdrop 960). */
    private fun heroArtRequests(id: String) = requestedImages.filter { it.contains("/Items/$id/") && (it.contains("maxWidth=900") || it.contains("maxWidth=960")) }
    /** Image requests for [id] at the row-card width (400). */
    private fun cardArtRequests(id: String) = requestedImages.filter { it.contains("/Items/$id/") && it.contains("maxWidth=400") }

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("driftfin_phone_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        PhoneSettings.init(ctx)
        PhoneSettings.setTitleLogos(false) // see class doc: removes the text-vs-logo fetch race
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return json("""{"Items":[],"TotalRecordCount":0}""")
                val path = url.encodedPath
                if (path.contains("/Images/")) {
                    requestedImages += path + "?" + (url.encodedQuery ?: "")
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "X", "2000")))
                }
                if (path.contains("/UserViews")) return json("""{"Items":[{"Id":"lib1","Name":"Movies","CollectionType":"movies"}],"TotalRecordCount":1}""")
                if (path.endsWith("/Items") && url.queryParameter("filters") == "IsUnplayed") return json(heroJson)
                if (path.contains("/UserItems/Resume")) return json(resumeJson)
                if (path.contains("/Items/Latest")) return json("""[]""")
                return json("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun showHome() { rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } } }

    /** Home's LazyColumn is the one tall vertical lazy list (section rows are short horizontal ones). */
    private fun homeList() = hasScrollToIndexAction() and SemanticsMatcher("home list") { it.boundsInRoot.height > 1000f }

    @Test fun offLetsAnAnimatedTitleBeTheHero() {
        heroJson = """{"Items":[${movie("a1", "Cartoon Film", "Animation")}],"TotalRecordCount":1}"""
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Cartoon Film").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Cartoon Film").assertIsDisplayed()
        // The witness is awaited, not sampled: the coil fetch is async and can lag the text composition.
        awaitReal(30_000) { heroArtRequests("a1").isNotEmpty() }
    }

    @Test fun onKeepsAnAnimatedTitleOutOfTheHero() {
        heroJson = """{"Items":[${movie("a1", "Cartoon Film", "Animation")}],"TotalRecordCount":1}"""
        resumeJson = """{"Items":[${movie("c1", "Resume Film", "Drama")}],"TotalRecordCount":1}"""
        PhoneSettings.setExcludeHomeAnimation(true)
        showHome()
        // Wait until the Continue Watching card actually rendered - the same load pass that filtered the hero.
        awaitReal(30_000) { cardArtRequests("c1").isNotEmpty() }
        // The only hero candidate is animated: no hero was composed, so its art was never fetched.
        assertTrue("hero art must never be fetched for the excluded title", heroArtRequests("a1").isEmpty())
        assertTrue("animated title must not appear on Home", rule.onAllNodesWithText("Cartoon Film").fetchSemanticsNodes().isEmpty())
        // The rest of Home still renders.
        rule.onNode(homeList()).performScrollToNode(hasText("Continue Watching"))
        rule.onNodeWithText("Continue Watching").assertIsDisplayed()
    }

    @Test fun onLeavesRowsUntouched() {
        heroJson = """{"Items":[${movie("a1", "Cartoon Film", "Animation")}],"TotalRecordCount":1}"""
        resumeJson = """{"Items":[${movie("a1", "Cartoon Film", "Animation")}],"TotalRecordCount":1}"""
        PhoneSettings.setExcludeHomeAnimation(true)
        showHome()
        // The same animated title is filtered from the hero but still rendered as a Continue Watching card.
        awaitReal(30_000) { cardArtRequests("a1").isNotEmpty() }
        assertTrue("animated title still renders in Continue Watching", cardArtRequests("a1").isNotEmpty())
        assertTrue("hero art must never be fetched for the excluded title", heroArtRequests("a1").isEmpty())

        // Pixel evidence: scrolled to Continue Watching, the frame shows the animated card with no hero above it.
        rule.onNode(homeList()).performScrollToNode(hasText("Continue Watching"))
        rule.onNodeWithText("Continue Watching").assertIsDisplayed()
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/render-check"); out.mkdirs()
        java.io.File(out, "hero-animation-excluded.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("hero-animation-excluded.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun onStillShowsNonAnimatedHeroes() {
        heroJson = """{"Items":[${movie("r1", "Regular Film", "Drama")}],"TotalRecordCount":1}"""
        PhoneSettings.setExcludeHomeAnimation(true)
        showHome()
        awaitReal(30_000) { rule.onAllNodesWithText("Regular Film").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Regular Film").assertIsDisplayed()
        // The witness is awaited, not sampled: the coil fetch is async and can lag the text composition.
        awaitReal(30_000) { heroArtRequests("r1").isNotEmpty() }
    }
}
