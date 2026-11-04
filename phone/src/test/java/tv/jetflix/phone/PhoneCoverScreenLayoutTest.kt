package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import tv.jetflix.phone.data.PhoneSettings
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen

/**
 * Folding-phone cover screens (for example the Razr+ 2025 outer display, 1272x1080 px at ~417 dpi,
 * about 411x485 dp) are about as wide as a phone but half as tall. The Home hero and the Details
 * backdrop header were sized from width alone, so on a cover screen they ate the whole display and
 * pushed the title and Play off the bottom edge. Both are now capped by screen height; normal phones
 * keep the exact width-driven sizes.
 *
 * Pinned numbers (screen width 411 dp):
 *  - hero: min((411-32)/0.8, 62% of height) -> 300.7 dp on the cover screen, 473.75 dp on 411x891.
 *  - details header: min(411/1.4, 45% of height) -> 218.25 dp on the cover screen, 293.57 dp on 411x891.
 *
 * Title logos are disabled so the title is always a text node (the mock server answers a valid PNG
 * for every /Images/ request, which would otherwise race text against a logo image).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneCoverScreenLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer

    /** False: Home fixture. True: Details fixture (every non-image request answers the movie). */
    private var detailsMode = false

    private val heroJson = """{"Items":[{"Id":"h1","Name":"Cover Film","Type":"Movie","Genres":["Drama"],"CommunityRating":9.0,"BackdropImageTags":["b"],"ImageTags":{"Primary":"x"}}],"TotalRecordCount":1}"""
    private val movieJson = """{"Id":"m1","Name":"Synthetic Movie","Type":"Movie","ProductionYear":2021,"OfficialRating":"PG","RunTimeTicks":57000000000,"Genres":["Adventure"],"Overview":"A made-up overview for layout checks.","BackdropImageTags":["b"],"ImageTags":{"Primary":"p"},"UserData":{"IsFavorite":true}}"""

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
        ctx.getSharedPreferences("driftfin_phone_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        PhoneSettings.init(ctx)
        PhoneSettings.setTitleLogos(false) // see class doc: removes the text-vs-logo fetch race
        detailsMode = false
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return json("""{"Items":[],"TotalRecordCount":0}""")
                val path = url.encodedPath
                if (path.contains("/Images/")) {
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(TestArt.poster(1, "X", "2000")))
                }
                if (detailsMode) return json(movieJson)
                if (path.contains("/UserViews")) return json("""{"Items":[{"Id":"lib1","Name":"Movies","CollectionType":"movies"}],"TotalRecordCount":1}""")
                if (path.endsWith("/Items") && url.queryParameter("filters") == "IsUnplayed") return json(heroJson)
                if (path.contains("/Items/Latest")) return json("""[]""")
                return json("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @After fun teardown() { if (::server.isInitialized) server.shutdown() }

    private fun showHome() {
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } }
        awaitReal(30_000) { rule.onAllNodes(hasText("Cover Film")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun showDetails() {
        detailsMode = true
        rule.setContent { DriftfinTheme { DetailsScreen("m1", onBack = {}, onPlay = {}) } }
        awaitReal(30_000) { rule.onAllNodes(hasText("Synthetic Movie")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tagHeight(tag: String): Float =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height / rule.density.density

    private fun bottomDp(text: String): Float =
        rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.bottom / rule.density.density

    private fun viewportDp(): Float = rule.activity.resources.configuration.screenHeightDp.toFloat()

    @Test
    @Config(qualifiers = "w411dp-h485dp-xxhdpi")
    fun coverScreenHeroIsCappedAndPlayStaysOnScreen() {
        showHome()
        val h = tagHeight("home-hero-card")
        assertTrue("cover hero capped by screen height: $h", h in 298f..304f)
        rule.onNodeWithText("\u25b6  Play").assertIsDisplayed()
        assertTrue("Play on screen: bottom=${bottomDp("\u25b6  Play")} viewport=${viewportDp()}", bottomDp("\u25b6  Play") <= viewportDp())
    }

    @Test
    @Config(qualifiers = "w411dp-h485dp-xxhdpi")
    fun coverScreenDetailsHeaderIsCappedAndTitleAndPlayStayOnScreen() {
        showDetails()
        val h = tagHeight("details-header")
        assertTrue("cover details header capped by screen height: $h", h in 214f..222f)
        assertTrue("title on screen: bottom=${bottomDp("Synthetic Movie")}", bottomDp("Synthetic Movie") <= viewportDp())
        rule.onNodeWithText("\u25b6  Play").assertIsDisplayed()
        assertTrue("Play on screen: bottom=${bottomDp("\u25b6  Play")}", bottomDp("\u25b6  Play") <= viewportDp())
    }

    @Test
    fun normalPhoneHeroHeightUnchanged() {
        showHome()
        val h = tagHeight("home-hero-card")
        assertTrue("normal-phone hero keeps width/0.8: $h", h in 471f..476f)
    }

    @Test
    fun normalPhoneDetailsHeaderHeightUnchanged() {
        showDetails()
        val h = tagHeight("details-header")
        assertTrue("normal-phone details header keeps width/1.4: $h", h in 290f..297f)
    }
}
