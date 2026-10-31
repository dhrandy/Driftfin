package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme
import java.io.File

/** Details with heart and star icon buttons (no text): rendered pixels plus real taps on a synthetic title. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DetailsRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun iconButtonsRenderAndToggle() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().contains("/Images/")) {
                    val b = Bitmap.createBitmap(400, 280, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(b)
                    c.drawRect(0f, 0f, 400f, 280f, android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 400f, 280f, 0xFF2E5E8C.toInt(), 0xFFB8552E.toInt(), android.graphics.Shader.TileMode.CLAMP) })
                    val o = java.io.ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, o)
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(o.toByteArray()))
                }
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return json.setBody("""{"Id":"m1","Name":"Synthetic Movie","Type":"Movie","ProductionYear":2021,"OfficialRating":"PG","RunTimeTicks":57000000000,"Genres":["Adventure"],"Overview":"A made-up overview for layout checks.","BackdropImageTags":["b"],"ImageTags":{"Primary":"p"},"People":[{"Id":"p1","Name":"Synthetic Actor","Role":"Lead","Type":"Actor","PrimaryImageTag":"x"}],"UserData":{"IsFavorite":true},"RemoteTrailers":[{"Name":"Trailer","Url":"https://youtu.be/dQw4w9WgXcQ"}]}""")
            }
        }
        server.start()
        Graph.allowHardwareArt = false
        Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Graph.rebuildImagesForTest()
        Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
        var backed = false
        var trailered: tv.jetflix.phone.data.TrailerSource? = null
        rule.setContent { DriftfinTheme { DetailsScreen("m1", onBack = { backed = true }, onPlay = {}, onTrailer = { trailered = it }) } }
        val end = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }
        rule.onNodeWithContentDescription("Add to watchlist").assertIsDisplayed()
        rule.onNodeWithContentDescription("In favorites. Tap to remove").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add to watchlist").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("In watchlist. Tap to remove").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithContentDescription("Watch trailer").assertIsDisplayed()
        // Trailer button matches the TV proportions: wider than tall (measured on the real screen).
        val trailerBounds = rule.onNodeWithContentDescription("Watch trailer").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("trailer button should be wider than tall: $trailerBounds", trailerBounds.width > trailerBounds.height)
        rule.onNodeWithContentDescription("Watch trailer").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("dQw4w9WgXcQ", (trailered as? tv.jetflix.phone.data.TrailerSource.YouTube)?.videoId)
        // The title logo image replaced the text: no text node carries the title, and the image node has real bounds.
        org.junit.Assert.assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("Synthetic Movie"), useUnmergedTree = true).fetchSemanticsNodes().size)
        val logoNode = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Synthetic Movie"), useUnmergedTree = true).fetchSemanticsNodes().single()
        org.junit.Assert.assertTrue("logo image has real bounds: ${'$'}logoNode.boundsInRoot", logoNode.boundsInRoot.height > 100)
        val d = rule.activity.window.decorView
        d.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        d.layout(0, 0, 1080, 2400)
        val b = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        d.draw(android.graphics.Canvas(b))
        File("build/render-check").mkdirs()
        File("build/render-check/details.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithContentDescription("Back").performClick()
        org.junit.Assert.assertTrue("Back icon fires onBack", backed)
        server.shutdown()
    }

}
