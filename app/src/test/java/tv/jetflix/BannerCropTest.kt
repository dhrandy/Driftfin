package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.ui.components.ArtImage
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.HeaderArtAlignment
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Details header banner crop: the 460dp header box is wider (2.09:1) than 16:9 art, so Crop drops
 * vertical slices. the user's Shrinking photo showed the subject's top clipped by centering; the header
 * must anchor the TOP of the art (TopCenter) so heads/titles at the top of fanart stay visible.
 * Proof: synthetic 1600x900 art with a red band only in its top 30px - visible only under TopCenter.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class BannerCropTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun headerShowsTopOfArt() {
        // 1600x900 art: red only in the top 30 source px, blue elsewhere.
        val art = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(art)
        val p = android.graphics.Paint()
        p.color = 0xFF2020C8.toInt(); c.drawRect(0f, 0f, 1600f, 900f, p)
        p.color = 0xFFD82020.toInt(); c.drawRect(0f, 0f, 1600f, 30f, p)

        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { BitmapPainter(art.asImageBitmap()) }) {
                JetflixTheme {
                    // Exact header geometry from DetailsScreen: fillMaxWidth x 460dp, Crop, HeaderArtAlignment.
                    Box(Modifier.fillMaxWidth().height(460.dp)) {
                        ArtImage(
                            url = "http://demo.test/backdrop",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            alignment = HeaderArtAlignment,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))

        // Top marker band (30/900 of art) must be visible at the very top of the header.
        val top = bmp.getPixel(bmp.width / 2, 6)
        val red = android.graphics.Color.red(top)
        val blue = android.graphics.Color.blue(top)
        println("BANNER_TOP_PIXEL r=$red b=$blue")
        assertTrue("top-of-art marker band must be visible at the header top (was r=$red b=$blue)", red > 150 && blue < 120)
        // And the band ends quickly (thin marker, not the whole header).
        val mid = bmp.getPixel(bmp.width / 2, 200)
        assertTrue("header mid should be the blue body of the art", android.graphics.Color.blue(mid) > 120 && android.graphics.Color.red(mid) < 120)
    }
}
