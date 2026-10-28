package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Randy on build 22: on Search "you can't see the search icon, it doesn't turn grey" - the magnifier
 * was hardcoded white, invisible on the white/light pill. It now follows the pill's content color:
 * dark on the selected and focused pill, light on the plain bar. Pixel-verified in all three states.
 */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchIconContrastTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun lum(px: IntArray, w: Int, fx: Double, fy: Double): Double {
        // 3x3 patch average around the fractional point, so anti-aliasing does not pick the sample.
        var sum = 0.0; var n = 0
        val cx = (fx * (w - 1)).toInt(); val cy = (fy * (px.size / w - 1)).toInt()
        for (dy in -1..1) for (dx in -1..1) {
            val x = (cx + dx).coerceIn(0, w - 1); val y = (cy + dy).coerceIn(0, px.size / w - 1)
            val c = px[y * w + x]
            sum += 0.299 * ((c shr 16) and 0xFF) / 255.0 + 0.587 * ((c shr 8) and 0xFF) / 255.0 + 0.114 * (c and 0xFF) / 255.0
            n++
        }
        return sum / n
    }

    // The semantics bounds include the Canvas padding (12dp sides, 5dp top/bottom around the 18dp
    // drawing): drawing-space fraction (dx,dy) maps to bounds fraction ((12+dx*18)/42, (5+dy*18)/28).
    private fun draw(fx: Double, fy: Double): Pair<Double, Double> = (12.0 + fx * 18.0) / 42.0 to (5.0 + fy * 18.0) / 28.0

    /** Stroke points on the magnifier (circle at 0.4/0.4 r=0.34, tail line 0.66->0.95) vs empty corners. */
    private fun strokeLum(px: IntArray, w: Int) = listOf(0.74 to 0.40, 0.40 to 0.74, 0.06 to 0.40, 0.40 to 0.06, 0.80 to 0.80).map { (x, y) -> val (a, b) = draw(x, y); lum(px, w, a, b) }.average()
    private fun backgroundLum(px: IntArray, w: Int) = listOf(0.10 to 0.90, 0.92 to 0.10).map { (x, y) -> val (a, b) = draw(x, y); lum(px, w, a, b) }.average()

    private fun glyphPixels(): Pair<IntArray, Int> {
        rule.waitForIdle()
        val b = rule.onNodeWithContentDescription("Search").fetchSemanticsNode().boundsInRoot
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val l = b.left.toInt().coerceIn(0, bmp.width - 1); val t = b.top.toInt().coerceIn(0, bmp.height - 1)
        val r = b.right.toInt().coerceIn(l + 1, bmp.width); val bot = b.bottom.toInt().coerceIn(t + 1, bmp.height)
        val px = IntArray((r - l) * (bot - t))
        bmp.getPixels(px, 0, r - l, l, t, r - l, bot - t)
        return px to (r - l)
    }

    private fun bar(selected: JetflixTab, focusSearch: Boolean) {
        rule.setContent { JetflixTheme { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(tv.jetflix.app.ui.theme.JetflixColors.Background)) { TopNavBar(selected = selected, userName = "demo", onTabSelected = {}, onUserClick = {}) } } }
        rule.waitForIdle()
        if (focusSearch) { rule.onNodeWithContentDescription("Search").requestFocus(); rule.waitForIdle() }
    }

    @Test fun glyphIsDarkOnTheSelectedPill() {
        bar(JetflixTab.SEARCH, focusSearch = false)
        val (px, w) = glyphPixels()
        assertTrue("selected pill is white; glyph must be dark (stroke=${strokeLum(px, w)}, pill=${backgroundLum(px, w)})", strokeLum(px, w) < 0.3 && backgroundLum(px, w) > 0.7)
    }

    @Test fun glyphIsDarkOnTheFocusedPill() {
        bar(JetflixTab.HOME, focusSearch = true)
        val (px, w) = glyphPixels()
        assertTrue("focused pill is light grey; glyph must be dark (stroke=${strokeLum(px, w)}, pill=${backgroundLum(px, w)})", strokeLum(px, w) < 0.35 && backgroundLum(px, w) > 0.6)
    }

    @Test fun glyphIsLightOnThePlainBar() {
        bar(JetflixTab.HOME, focusSearch = false)
        val (px, w) = glyphPixels()
        assertTrue("plain bar is dark; glyph must be light (stroke=${strokeLum(px, w)}, bar=${backgroundLum(px, w)})", strokeLum(px, w) > 0.6 && backgroundLum(px, w) < 0.4)
    }
}
