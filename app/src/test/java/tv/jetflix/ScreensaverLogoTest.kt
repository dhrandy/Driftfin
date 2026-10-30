package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.components.ScreensaverSlide
import tv.jetflix.app.ui.components.ScreensaverText
import tv.jetflix.app.ui.theme.JetflixTheme

/** Randy on build 40: "for the screensaver can we use the title logo?" The slide shows the title's
 * logo art when the server has it and falls back to the plain-text title when it does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
class ScreensaverLogoTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val logo = "http://demo.test/Items/m1/Images/Logo?maxWidth=600"
    private val slide = ScreensaverSlide("http://demo.test/Items/m1/Images/Backdrop", "Demo Title", "An overview.", logo)

    private fun show(art: (String?) -> androidx.compose.ui.graphics.painter.Painter?) = rule.setContent {
        CompositionLocalProvider(LocalTestArtwork provides art) {
            JetflixTheme { ScreensaverText(slide, onReading = {}) }
        }
    }

    @Test fun serverLogoReplacesTheTextTitle() {
        val bmp = Bitmap.createBitmap(128, 32, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFFCC3333.toInt()) }
        show { url -> if (url == logo) BitmapPainter(bmp.asImageBitmap()) else null }
        rule.waitForIdle()
        // The logo image carries the title as its content description; the text title is gone.
        rule.onAllNodes(hasContentDescription("Demo Title")).assertCountEquals(1)
        rule.onAllNodes(hasText("Demo Title")).assertCountEquals(0)
    }

    @Test fun noLogoKeepsTheTextTitle() {
        show { null }
        rule.waitForIdle()
        rule.onAllNodes(hasText("Demo Title")).assertCountEquals(1)
    }
}
