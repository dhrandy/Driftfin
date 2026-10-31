package tv.jetflix.phone

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.SettingsScreen
import tv.jetflix.phone.update.WhatsNewDialog
import java.io.File

/** Pixel evidence for the What's New dialog and the Settings "Who's watching?" list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneVisualCheckTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        assertTrue("$name is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun whatsNewDialogRenders() {
        rule.setContent { DriftfinTheme { WhatsNewDialog(listOf("First change", "Second change", "Third change")) {} } }
        rule.waitForIdle()
        rule.onNodeWithText("What's new").assertIsDisplayed()
        rule.onNodeWithText("•  Second change").assertIsDisplayed()
        // The dialog lives in its own window, so draw the dialog window's decor view, not the activity's.
        val decor = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.AT_MOST))
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        val bmp = Bitmap.createBitmap(decor.measuredWidth, decor.measuredHeight, Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = File("build/render-check"); out.mkdirs()
        File(out, "whatsnew.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 4) for (y in 0 until bmp.height step 4) colors.add(bmp.getPixel(x, y))
        assertTrue("whatsnew.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }

    @Test fun settingsShowsWhosWatchingList() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.init(ctx)
        Graph.signOut()
        Graph.tokens = tv.jetflix.phone.data.TokenStore(ctx, object : tv.jetflix.phone.data.TokenCrypto {
            override fun encrypt(plain: String) = "x$plain"
            override fun decrypt(blob: String) = blob.removePrefix("x")
        })
        val base = "http://10.0.2.2:1"
        Graph.tokens.save(base, "ua", "Ava", "tokA")
        Graph.tokens.save(base, "ub", "Ben", "tokB")
        Graph.signIn(Graph.tokens.load()!!)
        rule.setContent { DriftfinTheme { SettingsScreen() } }
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) { rule.waitForIdle(); org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50) }
        rule.onNodeWithText("Check for updates").assertIsDisplayed()
        // Updates stay first: above every preference row.
        val updatesTop = rule.onNodeWithText("Check for updates").fetchSemanticsNode().boundsInRoot.top
        val autoplayTop = rule.onNodeWithText("Autoplay next episode").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Check for updates is above the preferences", updatesTop < autoplayTop)
        rule.onNodeWithText("Who's watching?").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ava").performScrollTo().assertIsDisplayed()
        capture("settings-profiles.png")
        Graph.signOut()
    }
}
