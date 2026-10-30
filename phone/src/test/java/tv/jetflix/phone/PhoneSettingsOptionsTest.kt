package tv.jetflix.phone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.phone.data.PhoneSettings
import tv.jetflix.phone.data.PosterSize
import tv.jetflix.phone.ui.DriftfinTheme
import tv.jetflix.phone.ui.SettingsScreen

/** The Settings options: shown on screen, flipping works, and every choice survives a restart (fresh init over the same prefs). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneSettingsOptionsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun clean() {
        ctx.getSharedPreferences("driftfin_phone_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.init(ctx)
        PhoneSettings.init(ctx) // reread the cleared prefs so each test starts from defaults
        Graph.signOut()
    }

    @Test fun choicesSurviveRestart() {
        PhoneSettings.setPosterSize(PosterSize.Large)
        PhoneSettings.setFormatBadges(false)
        PhoneSettings.setHeroRatings(false)
        PhoneSettings.setShowNextUp(false)
        PhoneSettings.setShowFavorites(false)
        PhoneSettings.init(ctx) // a new process rereads the same preferences
        assertEquals(PosterSize.Large, PhoneSettings.posterSize.value)
        assertEquals(2, PhoneSettings.posterSize.value.gridColumns)
        assertFalse(PhoneSettings.formatBadges.value)
        assertFalse(PhoneSettings.heroRatings.value)
        assertFalse(PhoneSettings.showNextUp.value)
        assertFalse(PhoneSettings.showFavorites.value)
    }

    @Test fun defaultsMatchTv() {
        assertEquals(PosterSize.Medium, PhoneSettings.posterSize.value)
        assertEquals(3, PhoneSettings.posterSize.value.gridColumns)
        assertEquals(true, PhoneSettings.formatBadges.value)
        assertEquals(true, PhoneSettings.heroRatings.value)
        assertEquals(true, PhoneSettings.showNextUp.value)
        assertEquals(true, PhoneSettings.showFavorites.value)
    }

    @Test fun settingsScreenListsAndFlipsOptions() {
        rule.setContent { DriftfinTheme { SettingsScreen() } }
        rule.waitForIdle()
        rule.onNodeWithText("Check for updates").assertIsDisplayed() // still first
        rule.onNodeWithText("Poster size: Medium").assertIsDisplayed()
        rule.onNodeWithText("Format badges").assertIsDisplayed()
        rule.onNodeWithText("Hero ratings").assertIsDisplayed()
        rule.onNodeWithText("Next Up row").assertIsDisplayed()
        rule.onNodeWithText("Favorites row").assertIsDisplayed()
        rule.onNodeWithText("Poster size: Medium").performClick()
        rule.waitForIdle()
        assertEquals(PosterSize.Large, PhoneSettings.posterSize.value)
        rule.onNodeWithText("Poster size: Large").assertIsDisplayed()

        // Pixel evidence: draw the full settings hierarchy to a bitmap.
        val decor = rule.activity.window.decorView
        decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 1080, 2400)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val out = java.io.File("build/render-check"); out.mkdirs()
        java.io.File(out, "settings-options.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val colors = HashSet<Int>()
        for (x in 0 until bmp.width step 6) for (y in 0 until bmp.height step 6) colors.add(bmp.getPixel(x, y))
        org.junit.Assert.assertTrue("settings-options.png is not a blank frame (${colors.size} colors)", colors.size > 3)
    }
}
