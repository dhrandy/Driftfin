package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.SkipIntroButton
import java.io.File

/** Pixel evidence for the compact dark Skip Intro prompt over a black player. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSkipIntroPixelTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun skipIntroPromptRendersOverBlack() {
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    SkipIntroButton(onSkip = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 56.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Skip Intro").assertIsDisplayed()
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "skipintro.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Bottom-right quarter must hold the light prompt text/outline against the black player.
        var light = 0; var dark = 0
        for (x in 810 until 1080 step 3) for (y in 1800 until 2400 step 3) {
            val c = bmp.getPixel(x, y)
            val lum = (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / 3
            if (lum > 120) light++ else dark++
        }
        assertTrue("prompt pixels present (light=$light dark=$dark)", light > 50 && dark > 1000)
    }

    @Test fun landscapePlayerChromePlacesPromptBottomRight() {
        rule.setContent {
            DriftfinTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    androidx.compose.foundation.layout.Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = {}, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color(0x66000000), contentColor = Color.White)) { androidx.compose.material3.Text("Audio · Subtitles") }
                    }
                    SkipIntroButton(onSkip = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 56.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Skip Intro").assertIsDisplayed()
        rule.onNodeWithText("Audio · Subtitles").assertIsDisplayed()
        // Landscape phone: 2400x1080.
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 2400, 1080)
        val bmp = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "skipintro-landscape.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // The prompt must sit in the bottom-right quadrant of the landscape frame.
        var light = 0
        for (x in 1800 until 2400 step 3) for (y in 800 until 1080 step 3) {
            val c = bmp.getPixel(x, y)
            if ((android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / 3 > 120) light++
        }
        assertTrue("prompt pixels in bottom-right landscape quadrant (light=$light)", light > 50)
    }
}
