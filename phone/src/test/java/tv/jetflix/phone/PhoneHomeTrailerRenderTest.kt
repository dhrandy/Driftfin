package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.HomeScreen
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** The Home banner's Trailer button, rendered end to end: the hero request must ask Jellyfin for
 *  RemoteTrailers (a list response without that field carries no trailer links at all), and a hero
 *  with a trailer link must show the clapperboard icon in the banner's pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneHomeTrailerRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private lateinit var server: MockWebServer

    @Test fun bannerRequestsTrailersAndShowsIcon() {
        val heroFields = AtomicReference<String?>(null)
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("/Images/")) {
                    val b = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(b)
                    c.drawRect(0f, 0f, 400f, 500f, android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 400f, 500f, 0xFF1F3A5F.toInt(), 0xFF5F2E1F.toInt(), android.graphics.Shader.TileMode.CLAMP) })
                    val o = java.io.ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, o)
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(o.toByteArray()))
                }
                val json = MockResponse().setHeader("Content-Type", "application/json")
                val url = request.requestUrl!!
                val epath = url.encodedPath
                seen.add(epath)
                if (epath.endsWith("/Items") && url.queryParameter("includeItemTypes") == "Movie" && url.queryParameter("filters") == "IsUnplayed") {
                    // Nothing else on Home asks for unplayed movies (the More-like row's last-played
                    // fetch filters IsPlayed), so only the banner's hero fetch carries the field list this test guards.
                    val f = url.queryParameter("fields") ?: return json.setBody("""{"Items":[],"TotalRecordCount":0}""")
                    heroFields.set(f)
                    return json.setBody("""{"Items":[{"Id":"m1","Name":"Synthetic Hero","Type":"Movie","ProductionYear":2024,"OfficialRating":"PG-13","Genres":["Drama"],"Overview":"A made-up hero for the banner check.","ImageTags":{"Primary":"p"},"BackdropImageTags":["b"],"RemoteTrailers":[{"Name":"Trailer","Url":"https://youtu.be/dQw4w9WgXcQ"}]}],"TotalRecordCount":1}""")
                }
                if (epath.contains("/Items/Latest")) return json.setBody("[]")
                return json.setBody("""{"Items":[],"TotalRecordCount":0}""")
            }
        }
        server.start()
        Graph.allowHardwareArt = false
        Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Graph.rebuildImagesForTest()
        Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
        rule.setContent { DriftfinTheme { HomeScreen(onOpen = {}) } }
        val end = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }

        // The root cause guard: the hero fetch must request the RemoteTrailers field.
        val fields = heroFields.get().orEmpty().split(",")
        assertTrue("hero request fields must include RemoteTrailers (was: ${heroFields.get()}; seen=$seen)", fields.contains("RemoteTrailers"))

        // The banner shows the clapperboard for a hero with a trailer link.
        val node = rule.onNodeWithContentDescription("Watch trailer")
        node.assertIsDisplayed()

        // Pixel proof: the icon's own area has the white clapperboard strokes in the captured frame.
        val bounds = node.getBoundsInRoot()
        val density = rule.activity.resources.displayMetrics.density
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", "home-hero-trailer.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val l = (bounds.left.value * density).toInt().coerceAtLeast(0)
        val t = (bounds.top.value * density).toInt().coerceAtLeast(0)
        val r = (bounds.right.value * density).toInt().coerceAtMost(bmp.width)
        val b = (bounds.bottom.value * density).toInt().coerceAtMost(bmp.height)
        var white = 0
        for (y in t until b) for (x in l until r) {
            val px = bmp.getPixel(x, y)
            if ((px shr 16 and 0xFF) > 200 && (px shr 8 and 0xFF) > 200 && (px and 0xFF) > 200) white++
        }
        assertTrue("expected white clapperboard pixels in the trailer icon area (found $white)", white > 20)
    }

    @org.junit.After fun teardown() { server.shutdown() }
}
