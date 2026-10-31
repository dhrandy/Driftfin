package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.PlayerCenterControls
import tv.jetflix.phone.ui.PlayerChrome
import tv.jetflix.phone.ui.PlayerProgressBar
import java.io.File

/** Full-screen capture of the custom player controls: chrome, center play/pause with skips and the
 *  TV-style progress bar over a playing surface, for final pixel review before release. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-xxhdpi")
class PhonePlayerControlsCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun playerControlsFullScreen() {
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF182030))) {
                    PlayerChrome(visible = true, fill = true, onBack = {}, onAudioSubtitles = {}, onToggleZoom = {})
                    PlayerCenterControls(visible = true, playing = true, onTogglePlay = {}, onSkipBack = {}, onSkipForward = {}, modifier = Modifier.align(Alignment.Center))
                    PlayerProgressBar(visible = true, positionMs = 754_000L, bufferedMs = 1_800_000L, durationMs = 2_700_000L, onSeekToFraction = {}, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Audio · Subtitles").assertIsDisplayed()
        rule.onNodeWithText("-10s").assertIsDisplayed()
        rule.onNodeWithText("+10s").assertIsDisplayed()
        rule.onNodeWithText("12:34").assertIsDisplayed()
        rule.onNodeWithText("-32:26").assertIsDisplayed()
        rule.onNodeWithText("Fit").assertIsDisplayed()

        val decor = rule.activity.window.decorView
        val dm = rule.activity.resources.displayMetrics
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dm.heightPixels, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        File("build/render-check").mkdirs()
        File("build/render-check", "player-controls.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // The played part of the seek bar is the Driftfin red, in the lower strip of the frame.
        var red = 0
        for (y in (bmp.height * 0.8).toInt() until bmp.height) for (x in 0 until bmp.width) {
            val px = bmp.getPixel(x, y)
            if ((px shr 16 and 0xFF) > 180 && (px shr 8 and 0xFF) < 60 && (px and 0xFF) < 60) red++
        }
        assertTrue("played seek bar pixels are red (found $red)", red > 50)
    }
}
