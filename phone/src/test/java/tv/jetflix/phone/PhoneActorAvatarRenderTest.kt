package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.ActorAvatar
import tv.jetflix.phone.ui.DriftfinTheme
import java.io.File

/** Real-pixel render of the actor placeholder disc: gradient direction, circle clip, initials glyph. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneActorAvatarRenderTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun near(p: Int, r: Int, g: Int, b: Int, tol: Int = 14): Boolean =
        Math.abs(android.graphics.Color.red(p) - r) <= tol &&
        Math.abs(android.graphics.Color.green(p) - g) <= tol &&
        Math.abs(android.graphics.Color.blue(p) - b) <= tol &&
        android.graphics.Color.alpha(p) == 255

    private fun draw(name: String): Bitmap {
        rule.setContent {
            DriftfinTheme {
                androidx.compose.material3.Surface(color = androidx.compose.ui.graphics.Color(0xFFFF00FF), modifier = androidx.compose.ui.Modifier) {
                    Box { ActorAvatar(null, name, Modifier.size(72.dp).clip(CircleShape)) }
                }
            }
        }
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 600)
        val bmp = Bitmap.createBitmap(1080, 600, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        return bmp
    }

    private fun locate(bmp: Bitmap): IntArray {
        var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
            // gradient runs #4A5468 (top) to #1E2129 (bottom); both lean blue, the sentinel background does not
            if (r in 0x18..0x50 && g in 0x1B..0x5A && b in 0x23..0x6E && b - r >= 8 && android.graphics.Color.alpha(p) == 255) {
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        return intArrayOf(minX, minY, maxX, maxY)
    }

    @Test fun placeholderDiscGradientClipAndInitials() {
        val bmp = draw("Keanu Reeves")
        val out = File("build/render-check"); out.mkdirs()
        File(out, "actor-avatar.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val (minX, minY, maxX, maxY) = locate(bmp)
        assertTrue("disc found", maxX > minX && maxY > minY)
        val w = maxX - minX + 1
        val cx = (minX + maxX) / 2
        // gradient: top inside the disc is near #4A5468, bottom near #1E2129, and top is lighter
        val top = bmp.getPixel(cx, minY + w / 8)
        val bottom = bmp.getPixel(cx, maxY - w / 8)
        assertTrue("top of disc near #4A5468", near(top, 0x4A, 0x54, 0x68))
        assertTrue("bottom of disc near #1E2129", near(bottom, 0x1E, 0x21, 0x29))
        assertTrue("gradient runs light to dark", android.graphics.Color.red(top) > android.graphics.Color.red(bottom))
        // circle clip: the bounding-box corners are background, not gradient
        val corner = bmp.getPixel(minX + 2, minY + 2)
        assertTrue("corner outside the circle", !near(corner, 0x4A, 0x54, 0x68, 30) && !near(corner, 0x1E, 0x21, 0x29, 30))
        // initials glyph: bright #F0F0F0 pixels in the middle of the disc
        var glyph = 0
        for (y in (minY + w / 4)..(maxY - w / 4)) for (x in (minX + w / 4)..(maxX - w / 4)) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.red(p) > 220 && android.graphics.Color.green(p) > 220 && android.graphics.Color.blue(p) > 220) glyph++
        }
        assertTrue("initials glyph drawn ($glyph bright pixels)", glyph > 40)
    }

    @Test fun singleWordNameDrawsOneInitial() {
        val bmp = draw("Madonna")
        val (minX, minY, maxX, maxY) = locate(bmp)
        assertTrue("disc found", maxX > minX)
        val w = maxX - minX + 1
        var glyph = 0
        for (y in (minY + w / 4)..(maxY - w / 4)) for (x in (minX + w / 4)..(maxX - w / 4)) {
            val p = bmp.getPixel(x, y)
            if (android.graphics.Color.red(p) > 220 && android.graphics.Color.green(p) > 220 && android.graphics.Color.blue(p) > 220) glyph++
        }
        assertTrue("single initial drawn ($glyph bright pixels)", glyph > 40)
    }
}
