package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
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
import tv.jetflix.phone.ui.DetailsScreen
import tv.jetflix.phone.ui.DriftfinTheme
import java.io.File

/** The real phone cast row on a real details page: a photo-less actor renders the gradient initials
 *  disc at the row's own size and spacing, next to a photo'd actor, with their names below. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneCastPlaceholderRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun castRowRendersInitialsDiscForActorWithoutPhoto() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path.orEmpty().contains("/Images/")) {
                    val b = Bitmap.createBitmap(400, 280, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(b)
                    c.drawRect(0f, 0f, 400f, 280f, android.graphics.Paint().apply { color = 0xFF2E5E8C.toInt() })
                    val o = java.io.ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 100, o)
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(o.toByteArray()))
                }
                val json = MockResponse().setHeader("Content-Type", "application/json")
                return json.setBody("""{"Id":"m1","Name":"Synthetic Movie","Type":"Movie","ProductionYear":2021,"RunTimeTicks":57000000000,"Genres":["Drama"],"Overview":"Cast row placeholder check.","ImageTags":{"Primary":"p"},"People":[{"Id":"p1","Name":"Photo Actor","Role":"Lead","Type":"Actor","PrimaryImageTag":"x"},{"Id":"p2","Name":"Nophoto Person","Role":"Support","Type":"Actor"}],"UserData":{}}""")
            }
        }
        server.start()
        Graph.allowHardwareArt = false
        Graph.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Graph.rebuildImagesForTest()
        Graph.signIn(tv.jetflix.phone.data.Session(server.url("/").toString().trimEnd('/'), "u1", "Tester", "tok", "dev"))
        rule.setContent { DriftfinTheme { DetailsScreen("m1", onBack = {}, onPlay = {}, onTrailer = {}) } }
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }
        // the cast row is present with both actors' names
        rule.onNodeWithText("Photo Actor", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Nophoto Person", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Cast", substring = false, useUnmergedTree = true).assertIsDisplayed()
        // the disc sits directly above the actor's name in the 84.dp cast cell
        val nameBounds = rule.onNodeWithText("Nophoto Person", useUnmergedTree = true).getBoundsInRoot()
        val d = rule.activity.window.decorView
        d.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2200, android.view.View.MeasureSpec.EXACTLY))
        d.layout(0, 0, 1080, 2200)
        val bmp = Bitmap.createBitmap(1080, 2200, Bitmap.Config.ARGB_8888)
        d.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "details-cast-placeholder.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Locate the disc by its gradient in the column above the name node, then measure it:
        // the initials glyph interrupts the column mid-disc, so vertical extent is first-to-last
        // gradient pixel; width is the horizontal gradient extent at the disc's mid-height row.
        // xxhdpi = 3 px per dp; the 72.dp disc is 216px.
        val density = 3f
        val cx = (((nameBounds.left.value + nameBounds.right.value) / 2f) * density).toInt()
        val winBottom = ((nameBounds.top.value - 2f) * density).toInt().coerceAtMost(bmp.height - 1)
        val winTop = ((nameBounds.top.value - 150f) * density).toInt().coerceAtLeast(0)
        fun blueLean(p: Int): Boolean {
            val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
            return android.graphics.Color.alpha(p) == 255 && r in 0x18..0x50 && g in 0x1B..0x5A && b in 0x23..0x6E && b - r >= 8
        }
        // the name's center guides the search, but decor/root offsets shift absolute pixels: probe a
        // few columns around it and keep the one with the tallest gradient run (the disc)
        var bestX = cx; var colRows = emptyList<Int>()
        for (dx in -100..100 step 5) {
            val x = (cx + dx).coerceIn(0, bmp.width - 1)
            val rows = (winTop..winBottom).filter { blueLean(bmp.getPixel(x, it)) }
            if (rows.size > colRows.size) { colRows = rows; bestX = x }
        }
        assertTrue("placeholder disc found above the actor's name", colRows.isNotEmpty())
        val minY = colRows.first(); val maxY = colRows.last()
        val discH = maxY - minY + 1
        assertTrue("disc near 72dp cast size, was ${discH}px tall", discH in 190..235)
        val midY = (minY + maxY) / 2
        val rowXs = ((bestX - 160)..(bestX + 160)).filter { blueLean(bmp.getPixel(it, midY)) }
        assertTrue("disc has width at mid-height", rowXs.isNotEmpty())
        val discW = rowXs.last() - rowXs.first() + 1
        assertTrue("disc near 72dp cast size, was ${discW}px wide", discW in 190..235)
        fun near(p: Int, r: Int, g: Int, b: Int, tol: Int = 14): Boolean =
            Math.abs(android.graphics.Color.red(p) - r) <= tol &&
            Math.abs(android.graphics.Color.green(p) - g) <= tol &&
            Math.abs(android.graphics.Color.blue(p) - b) <= tol &&
            android.graphics.Color.alpha(p) == 255
        // top of the disc is the gradient's light end, bottom the dark end
        val top = bmp.getPixel(bestX, minY + 8)
        val bottom = bmp.getPixel(bestX, maxY - 8)
        assertTrue("top of disc near #4A5468, was #%08X".format(top), near(top, 0x4A, 0x54, 0x68))
        assertTrue("bottom of disc near #1E2129, was #%08X".format(bottom), near(bottom, 0x1E, 0x21, 0x29))
        assertTrue("gradient runs light to dark", android.graphics.Color.red(top) > android.graphics.Color.red(bottom))
        // circle clip: just outside the disc's left/right edges at mid-height is page background
        assertTrue("nothing gradient past the disc's right edge", !blueLean(bmp.getPixel(rowXs.last() + 12, midY)))
        assertTrue("nothing gradient past the disc's left edge", !blueLean(bmp.getPixel(rowXs.first() - 12, midY)))
        assertTrue("corner outside the circle", !blueLean(bmp.getPixel(bestX - 104, minY + 4)))
        // initials glyph: bright #F0F0F0 pixels in the middle of the disc
        var glyph = 0
        for (y in (minY + discH / 4)..(maxY - discH / 4)) for (x in (bestX - 54)..(bestX + 54)) {
            val px = bmp.getPixel(x, y)
            if (android.graphics.Color.red(px) > 220 && android.graphics.Color.green(px) > 220 && android.graphics.Color.blue(px) > 220) glyph++
        }
        assertTrue("initials glyph drawn ($glyph bright pixels)", glyph > 40)
        server.shutdown()
    }
}
