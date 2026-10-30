package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme
import java.util.concurrent.CopyOnWriteArrayList

/** Episode pages: the heart and the star act on the whole show, never on the episode itself. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneEpisodeActionsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer
    private val requested = CopyOnWriteArrayList<String>()
    @Volatile private var seriesFavorite = false
    @Volatile private var seriesFound = true
    @Volatile private var seriesCast = false
    @Volatile private var episodeCast = false

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
        Graph.rebuildImagesForTest()
        Graph.signOut()
        seriesFavorite = false
        seriesFound = true
        seriesCast = false
        episodeCast = false
        ctx.getSharedPreferences("jetflix_watchlist", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty(); requested += "${request.method} $p"
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return when {
                    p.contains("/Images/") -> MockResponse().setResponseCode(404)
                    p.contains("/Items/m1") -> json.setBody("""{"Id":"m1","Name":"A Movie","Type":"Movie","ProductionYear":2024,"ImageTags":{"Primary":"m"},"UserData":{"IsFavorite":false}}""")
                    p.contains("/Items/ep1") -> json.setBody("""{"Id":"ep1","Name":"The Pilot","Type":"Episode","SeriesId":"s1","SeasonId":"se1","SeriesName":"Some Show","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":25200000000,"Overview":"Episode overview.","ImageTags":{"Primary":"e"},"UserData":{"IsFavorite":false}${if (episodeCast) ""","People":[{"Id":"pa1","Name":"Episode Actor","Type":"Actor","Role":"Ep Role"}]""" else ""}}""")
                    p.contains("/Items/s1") ->
                        if (seriesFound) json.setBody("""{"Id":"s1","Name":"Some Show","Type":"Series","ProductionYear":2026,"ImageTags":{"Primary":"s"},"UserData":{"IsFavorite":$seriesFavorite}${if (seriesCast) ""","People":[{"Id":"pb1","Name":"Series Actor","Type":"Actor","Role":"Show Role"}]""" else ""}}""")
                        else MockResponse().setResponseCode(404).setBody("{}")
                    p.contains("/Shows/s1/Episodes") -> json.setBody("""{"Items":[{"Id":"e1","Name":"First","Type":"Episode","SeriesId":"s1","SeasonId":"se1","IndexNumber":1,"UserData":{"Played":true}},{"Id":"e2","Name":"Second","Type":"Episode","SeriesId":"s1","SeasonId":"se1","IndexNumber":2,"UserData":{"Played":true}},{"Id":"e3","Name":"Third","Type":"Episode","SeriesId":"s1","SeasonId":"se1","IndexNumber":3,"UserData":{"Played":false}}],"TotalRecordCount":3}""")
                    p.contains("UserFavoriteItems") -> json.setBody("""{"IsFavorite":true}""")
                    else -> json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                }
            }
        }
        server.start()
        Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
    }

    @Test fun heartTogglesTheSeriesNotTheEpisode() {
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithContentDescription("Add to favorites").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Add to favorites").performClick()
        awaitReal(10_000) { requested.any { it.contains("POST") && it.contains("UserFavoriteItems/s1") } }
        assertTrue("the episode itself was never favorited", requested.none { it.contains("UserFavoriteItems/ep1") })
        rule.onNodeWithContentDescription("In favorites. Tap to remove").assertIsDisplayed()
    }

    @Test fun episodeIdentityMatchesThePlayTarget() {
        var played: String? = null
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = { played = it }) } }
        awaitReal(15_000) { rule.onAllNodesWithText("S1:E1  \u00b7  The Pilot").fetchSemanticsNodes().isNotEmpty() }
        capture("episode-identity.png")
        rule.onNodeWithText("\u25b6  Play").performClick()
        rule.waitForIdle()
        assertTrue("Play opens the episode the identity line names", played == "ep1")
    }

    @Test fun heartReflectsTheSeriesState() {
        seriesFavorite = true
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithContentDescription("In favorites. Tap to remove").fetchSemanticsNodes().isNotEmpty() }
        capture("episode-series-actions.png")
        rule.onNodeWithContentDescription("In favorites. Tap to remove").performClick()
        awaitReal(10_000) { requested.any { it.contains("DELETE") && it.contains("UserFavoriteItems/s1") } }
        assertTrue(requested.none { it.contains("UserFavoriteItems/ep1") })
    }

    @Test fun heartHidesWhenTheShowCannotLoad() {
        seriesFound = false
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        // Give the episode and the failed series lookup time to settle.
        Thread.sleep(4000); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(500)
        assertTrue(rule.onAllNodesWithContentDescription("Add to favorites").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("In favorites. Tap to remove").fetchSemanticsNodes().isEmpty())
        assertTrue("the episode itself was never favorited", requested.none { it.contains("UserFavoriteItems/ep1") })
    }

    @Test fun watchedEpisodesShowACheck() {
        rule.setContent { DriftfinTheme { DetailsScreen("s1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithContentDescription("Watched").fetchSemanticsNodes().size >= 2 }
        capture("series-watched-episodes.png")
    }

    @Test fun starTogglesTheSeriesNotTheEpisode() {
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithContentDescription("Add to watchlist").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Add to watchlist").performClick()
        rule.waitForIdle()
        assertTrue("the series is starred", tv.jetflix.phone.data.Watchlist.ids.value.contains("s1"))
        assertTrue("the episode is not starred", !tv.jetflix.phone.data.Watchlist.ids.value.contains("ep1"))
        rule.onNodeWithContentDescription("In watchlist. Tap to remove").assertIsDisplayed()
    }

    @Test fun castFallsBackToTheSeriesWhenTheEpisodeHasNone() {
        seriesCast = true
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithText("Series Actor").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Cast").assertIsDisplayed()
        capture("episode-cast-fallback.png")
    }

    @Test fun episodeCastWinsOverTheSeriesCast() {
        seriesCast = true; episodeCast = true
        rule.setContent { DriftfinTheme { DetailsScreen("ep1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithText("Episode Actor").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2000); rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("the show's cast stays hidden when the episode has its own", rule.onAllNodesWithText("Series Actor").fetchSemanticsNodes().isEmpty())
    }

    @Test fun movieHeartTargetsTheMovie() {
        rule.setContent { DriftfinTheme { DetailsScreen("m1", onBack = {}, onPlay = {}) } }
        awaitReal(15_000) { rule.onAllNodesWithContentDescription("Add to favorites").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Add to favorites").performClick()
        awaitReal(10_000) { requested.any { it.contains("POST") && it.contains("UserFavoriteItems/m1") } }
        rule.onNodeWithContentDescription("In favorites. Tap to remove").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = android.graphics.Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        java.io.File("build/render-check").mkdirs()
        java.io.File("build/render-check", name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
