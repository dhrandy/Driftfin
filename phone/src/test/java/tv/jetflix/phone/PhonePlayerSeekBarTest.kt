package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.PlayerProgressBar
import tv.jetflix.phone.ui.PlayerSeekBar
import java.io.File

/** Phone player seek bar: the TV look measured in pixels (red played, gray buffered, dark track),
 *  time labels, and tap/drag seeking. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhonePlayerSeekBarTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun tvStyleBarMeasuredInPixels() {
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerSeekBar(positionMs = 90_000, bufferedMs = 300_000, durationMs = 600_000, onSeekToFraction = {}, modifier = Modifier.fillMaxWidth().testTag("seek"))
                }
            }
        }
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        capture("player-seek-bar.png")
        val bmp = android.graphics.BitmapFactory.decodeFile("build/render-check/player-seek-bar.png")
        fun kind(p: Int): Int {
            val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
            return when {
                r > 150 && g < 60 -> 1 // red played
                r in 100..150 && g in 100..150 && b in 100..150 -> 2 // 0x7A buffered
                r in 55..95 && g in 55..95 && b in 55..95 -> 3 // 0x4D track
                else -> 0
            }
        }
        // Find the bar's row: the row with the most red pixels (decor offsets shift absolute coordinates).
        var bestY = -1; var bestRed = 0
        for (y in 0 until bmp.height) {
            var red = 0
            for (x in 0 until bmp.width) if (kind(bmp.getPixel(x, y)) == 1) red++
            if (red > bestRed) { bestRed = red; bestY = y }
        }
        assertTrue("no red played layer found", bestRed > 40)
        var red = 0; var gray = 0; var track = 0
        for (x in 0 until bmp.width) when (kind(bmp.getPixel(x, bestY))) { 1 -> red++; 2 -> gray++; 3 -> track++ }
        val bar = red + gray + track
        // 1:30 of 10:00 played -> red is 15% of the bar; buffered to 5:00 -> red+gray is 50%.
        assertEquals(0.15, red.toDouble() / bar, 0.03)
        assertEquals(0.50, (red + gray).toDouble() / bar, 0.04)
        assertTrue("track fills the rest", track > bar * 0.4)
    }

    @Test fun timeLabelsShowPositionAndRemaining() {
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxWidth().background(Color.Black)) {
                    PlayerProgressBar(visible = true, positionMs = 90_000, bufferedMs = 300_000, durationMs = 600_000, onSeekToFraction = {}, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.onNodeWithText("1:30").assertExists()
        rule.onNodeWithText("-8:30").assertExists()
    }

    @Test fun hiddenBarRendersNothing() {
        rule.setContent {
            DriftfinTheme {
                PlayerProgressBar(visible = false, positionMs = 90_000, bufferedMs = 0, durationMs = 600_000, onSeekToFraction = {})
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag("player-seek-bar")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun tapSeeksToTappedFraction() {
        var frac: Float? = null
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerSeekBar(positionMs = 0, bufferedMs = 0, durationMs = 600_000, onSeekToFraction = { frac = it }, modifier = Modifier.fillMaxWidth().testTag("seek"))
                }
            }
        }
        rule.onNodeWithTag("seek").performClick()
        rule.runOnIdle { assertTrue("expected ~0.5, got $frac", frac != null && frac!! in 0.40f..0.60f) }
    }

    @Test fun dragSeeksToDroppedFraction() {
        var frac: Float? = null
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    PlayerSeekBar(positionMs = 0, bufferedMs = 0, durationMs = 600_000, onSeekToFraction = { frac = it }, modifier = Modifier.fillMaxWidth().testTag("seek"))
                }
            }
        }
        val node = rule.onNodeWithTag("seek")
        val b = node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput { swipe(Offset(b.left + 8, (b.top + b.bottom) / 2), Offset(b.left + b.width * 0.75f, (b.top + b.bottom) / 2), durationMillis = 400) }
        rule.runOnIdle { assertTrue("expected ~0.75, got $frac", frac != null && frac!! in 0.65f..0.85f) }
    }
}
