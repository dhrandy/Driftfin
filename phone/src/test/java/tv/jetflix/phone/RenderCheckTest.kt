package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.UserItemDataDto
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.PosterCard
import java.io.File

/** Real-pixel render of synthetic cards (no artwork, no server). Writes a PNG under build/ for a human to look at. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderCheckTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun postersRenderWatchedCheckAndResumeBar() {
        rule.setContent {
            DriftfinTheme {
              androidx.compose.material3.Surface(color = tv.jetflix.phone.ui.DriftfinColors.Background) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterCard(BaseItemDto(Id = "a", Name = "Synthetic A", Type = "Movie"), Modifier.width(112.dp)) {}
                    PosterCard(BaseItemDto(Id = "b", Name = "Synthetic B", Type = "Movie", UserData = UserItemDataDto(Played = true)), Modifier.width(112.dp)) {}
                    PosterCard(BaseItemDto(Id = "c", Name = "Synthetic C", Type = "Series", UserData = UserItemDataDto(PlayedPercentage = 40.0)), Modifier.width(112.dp)) {}
                }
              }
            }
        }
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 600)
        val bmp = Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "posters.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 4) for (y in 0 until bmp.height step 4) colors.add(bmp.getPixel(x, y))
        assertTrue("render is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun fullShellRendersTopBarAndBottomNav() {
        rule.setContent { DriftfinTheme { tv.jetflix.phone.ui.DriftfinShell() } }
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "shell.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("shell is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    /** A populated Home: a local fake server returns synthetic titles and generated gradient art, so the frosted bar blurs real pixels. */
    @Test fun populatedHomeWithGeneratedArt() {
        fun png(seed: Int): ByteArray {
            val b = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            val hues = floatArrayOf((seed * 47 % 360).toFloat(), 0.7f, 0.8f)
            val p = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 200f, 300f, android.graphics.Color.HSVToColor(hues), android.graphics.Color.HSVToColor(floatArrayOf((seed * 47 % 360 + 90f) % 360, 0.8f, 0.4f)), android.graphics.Shader.TileMode.CLAMP) }
            c.drawRect(0f, 0f, 200f, 300f, p)
            val o = java.io.ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, o); return o.toByteArray()
        }
        fun item(n: Int) = if (n % 2 == 0) """{"Id":"i$n","Name":"Synthetic Episode $n","Type":"Episode","SeriesId":"s$n","SeriesName":"Synthetic Show $n","SeriesPrimaryImageTag":"st","ImageTags":{"Primary":"still"},"UserData":{"Played":false,"PlayedPercentage":${if (n % 3 == 0) 45.0 else 0.0}}}""" else """{"Id":"i$n","Name":"Synthetic Title $n","Type":"Movie","ImageTags":{"Primary":"t"},"BackdropImageTags":["b"],"UserData":{"Played":${n % 4 == 0},"PlayedPercentage":${if (n % 3 == 0) 45.0 else 0.0}}}"""
        val items = (1..14).joinToString(",") { item(it) }
        val server = okhttp3.mockwebserver.MockWebServer()
        val imagePaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val path = request.path.orEmpty()
                if (path.contains("/Images/")) imagePaths += path
                return when {
                    path.contains("/Images/") -> okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "image/png")
                        .setBody(okio.Buffer().write(png((Regex("/Items/[is](\\d+)/").find(path)?.groupValues?.get(1)?.toInt() ?: 1) + (if (path.contains("/Items/s")) 5 else 0))))
                    path.contains("Latest") -> okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody("[$items]")
                    else -> okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[$items],"TotalRecordCount":14,"StartIndex":0}""")
                }
            }
        }
        server.start()
        tv.jetflix.phone.Graph.allowHardwareArt = false
        tv.jetflix.phone.Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        tv.jetflix.phone.Graph.rebuildImagesForTest()
        tv.jetflix.phone.Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
        rule.setContent { DriftfinTheme { tv.jetflix.phone.ui.DriftfinShell() } }
        val decor = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val colors = HashSet<Int>()
        fun render(): Int {
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 1080, 2400)
            decor.draw(android.graphics.Canvas(bmp))
            colors.clear()
            for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
            return colors.size
        }
        // Adaptive settle: wait until the things the assertions below check are actually observable,
        // instead of a fixed wall-clock window (slow shared runners need longer than 8s).
        val end = System.currentTimeMillis() + 30_000
        var colorCount = render()
        while (System.currentTimeMillis() < end && (colorCount <= 200 || imagePaths.none { it.contains("/Items/s") && it.contains("Primary") })) {
            Thread.sleep(250)
            colorCount = render()
        }
        val out = File("build/render-check"); out.mkdirs()
        File(out, "home-populated.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        server.shutdown()
        assertTrue("Continue Watching and Next Up asked for series posters: $imagePaths", imagePaths.any { it.contains("/Items/s") && it.contains("Primary") })
        assertTrue("no episode still was requested at poster-card size (the wide hero may still use one): $imagePaths", imagePaths.none { it.contains("maxWidth=400") && it.contains("tag=still") })
        assertTrue("populated home has art (${colors.size} colors)", colors.size > 200)
    }
}
