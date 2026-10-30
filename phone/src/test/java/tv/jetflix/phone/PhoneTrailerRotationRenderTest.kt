package tv.jetflix.phone

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.YouTubeTrailerScreen
import java.io.File

/** The real trailer screen, rendered at phone proportions: it asks for sensor rotation while
 *  showing, lays out full-bleed in portrait AND landscape, and Back restores portrait browsing. */
abstract class TrailerRotationBase {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    protected fun capture(name: String) {
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

    protected fun flow(png: String, exerciseExit: Boolean) {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        var showing by mutableStateOf(true)
        rule.setContent { if (showing) YouTubeTrailerScreen("dQw4w9WgXcQ", "Test Trailer", onClose = { showing = false }) else Text("Back to browsing") }
        rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("trailer asks the phone to follow its own rotation", ActivityInfo.SCREEN_ORIENTATION_SENSOR, rule.activity.requestedOrientation)
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        capture(png)
        if (exerciseExit) {
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper()
            rule.onNodeWithText("Back to browsing").assertIsDisplayed()
            assertEquals("leaving the trailer restores portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneTrailerRotationPortraitTest : TrailerRotationBase() {
    @Test fun portraitThenExitRestoresPortrait() = flow("trailer-portrait.png", exerciseExit = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-xxhdpi")
class PhoneTrailerRotationLandscapeTest : TrailerRotationBase() {
    @Test fun landscapeLayoutIsFullBleed() = flow("trailer-landscape.png", exerciseExit = false)
}
