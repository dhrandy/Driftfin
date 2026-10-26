package tv.jetflix

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.TitleOrLogo
import tv.jetflix.app.ui.theme.JetflixTheme
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression for the plain-text bug: the logo request must start and complete even though the
 * logo Image is only composed on Success. With a size-less request Coil 2.7 suspends until the
 * painter is drawn, which never happened - the fetcher was never called and the text showed forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TitleLogoCoilFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val fetches = AtomicInteger(0)

    private fun installFakeLoader() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = object : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher = Fetcher {
                fetches.incrementAndGet()
                val bmp = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
                DrawableResult(BitmapDrawable(ctx.resources, bmp), isSampled = false, dataSource = DataSource.MEMORY)
            }
        }
        Coil.setImageLoader(ImageLoader.Builder(ctx).components { add(factory) }.build())
    }

    @After fun resetLoader() {
        Coil.setImageLoader(ImageLoader.Builder(ApplicationProvider.getApplicationContext()).build())
    }

    @Test fun theLogoRequestStartsAndCompletesWithoutTheImageBeingDrawnFirst() {
        installFakeLoader()
        rule.setContent { JetflixTheme { TitleOrLogo(title = "Demo Title", logoUrl = "http://demo.test/Items/x/Images/Logo?maxWidth=600") } }
        // The request runs on Coil's executor; give it real time to complete.
        val end = System.currentTimeMillis() + 5000
        while (fetches.get() == 0 && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue("the logo fetcher was never called: the request deadlocked behind the success-only Image", fetches.get() > 0)
        rule.waitUntil(timeoutMillis = 5000) { fetches.get() > 0 }
        // On Success the logo Image (contentDescription = title) replaces the text. The fetch
        // finishing and the Success state reaching composition are separate steps, so wait for the
        // node itself (condition based, not a fixed sleep) before asserting.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription("Demo Title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Demo Title").assertIsDisplayed()
    }
}
