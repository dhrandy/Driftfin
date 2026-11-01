package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.TvmazeCastResolver
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme

/**
 * Episode details cast (user report, phone build 8: "season 2 actors not working on phone at all,
 * shows season 1"). The episode carries no People and the series cast is season 1's; the page must
 * show the verified TVMaze season's guests and never the series cast.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneDetailsCastTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private lateinit var tvmaze: MockWebServer
    @Volatile private var tvmazeFound = true

    @Before fun setup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.allowHardwareArt = false
        Graph.init(ctx)
        Graph.rebuildImagesForTest()
        Graph.signOut()
        tvmazeFound = true
        tvmaze = MockWebServer()
        tvmaze.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    !tvmazeFound -> json.setBody("""[]""") // search finds no show
                    p.contains("search/shows") -> json.setBody("""[{"show":{"id":200,"name":"Jury Duty Presents: Company Retreat"}}]""")
                    p.contains("/shows/200/episodes") -> json.setBody("""[
                        {"id":601,"name":"Onboarding","season":1,"number":1,"airdate":"2026-03-20"},
                        {"id":602,"name":"Team Building","season":1,"number":2,"airdate":"2026-03-27"}
                    ]""")
                    p.contains("/episodes/602/guestcast") -> json.setBody("""[
                        {"person":{"id":187924,"name":"Lisa Gilroy"},"character":{"name":"Christine"}}
                    ]""")
                    else -> json.setBody("""{}""")
                }
            }
        }
        tvmaze.start()
        TvmazeCastResolver.apiForTests = TvmazeCastResolver.apiFor(tvmaze.url("/").toString())
        TvmazeCastResolver.resetForTests()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                if (p.contains("/Images/")) return MockResponse().setResponseCode(404)
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    // The season 2 episode itself: no People on the server.
                    p.contains("/Items/ep2") -> json.setBody("""{"Id":"ep2","Type":"Episode","Name":"Team Building","SeriesId":"ser","SeriesName":"Jury Duty","SeasonId":"sea2","ParentIndexNumber":2,"IndexNumber":2,"PremiereDate":"2026-03-27T00:00:00.0000000Z"}""")
                    // The series carries only season 1's cast - the wrong list for this page.
                    p.contains("/Items/ser") -> json.setBody("""{"Id":"ser","Type":"Series","Name":"Jury Duty","People":[{"Id":"sp1","Name":"Season One Actor","Type":"Actor"}]}""")
                    p.contains("includeItemTypes=Season") -> json.setBody("""{"Items":[{"Id":"sea2","Type":"Season","Name":"Season 2","IndexNumber":2}],"TotalRecordCount":1}""")
                    p.contains("/Shows/ser/Episodes") -> json.setBody("""{"Items":[
                        {"Id":"e1","Type":"Episode","IndexNumber":1,"Name":"Onboarding","SeasonId":"sea2","PremiereDate":"2026-03-20T00:00:00.0000000Z"},
                        {"Id":"e2","Type":"Episode","IndexNumber":2,"Name":"Team Building","SeasonId":"sea2","PremiereDate":"2026-03-27T00:00:00.0000000Z"}
                    ],"TotalRecordCount":2}""")
                    p.contains("/Items") -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                    else -> json.setBody("""{}""")
                }
            }
        }
        server.start()
        SessionManager.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u", "N", "t", Graph.tokens.deviceId()))
    }

    @After fun teardown() {
        TvmazeCastResolver.apiForTests = null
        TvmazeCastResolver.resetForTests()
        server.shutdown()
        tvmaze.shutdown()
    }

    private fun waitFor(text: String) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(80)
        }
    }

    @Test fun season2EpisodeShowsVerifiedSeasonGuestsNotSeason1Cast() {
        rule.setContent { DriftfinTheme { DetailsScreen("ep2", onBack = {}, onPlay = {}) } }
        waitFor("Lisa Gilroy")
        rule.onNodeWithText("Lisa Gilroy").assertIsDisplayed()
        rule.onNodeWithText("Christine").assertIsDisplayed()
        // The wrong-season series cast never appears, not even before the lookup finished.
        assertEquals(0, rule.onAllNodes(hasText("Season One Actor")).fetchSemanticsNodes().size)
    }

    @Test fun unverifiedSeasonShowsNoCastRowInsteadOfSeason1Cast() {
        tvmazeFound = false
        rule.setContent { DriftfinTheme { DetailsScreen("ep2", onBack = {}, onPlay = {}) } }
        waitFor("Season 2") // the season row proves the page finished loading
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0, rule.onAllNodes(hasText("Season One Actor")).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasText("Cast")).fetchSemanticsNodes().size)
    }
}
