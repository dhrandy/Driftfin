package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/** User request: "probably even have the option to turn the banner off." Settings > Home & discovery gains
 *  a Home banner toggle (on by default); off hides the featured banner and Home starts with the rows. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeBannerToggleTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    private fun resetPrefs() {
        val c = RuntimeEnvironment.getApplication()
        c.getSharedPreferences("jetflix_settings", 0).edit().clear().commit()
        AppSettings.init(c)
    }

    private fun state(): HomeUiState {
        val hero = items.first { it.Name == "The Gold Rush" }
        return HomeUiState(
            loading = false,
            hero = hero,
            heroes = listOf(hero),
            sections = listOf(
                HomeSection("Continue Watching", items.take(6).map { it.copy(UserData = tv.jetflix.app.data.UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }, wide = true),
                HomeSection("Recently Added Movies", items.drop(2), wide = false),
            ),
        )
    }

    private fun showHome() {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent { JetflixTheme { HomeContent(state(), repo, "demo", {}, {}, {}, {}) } }
        rule.waitForIdle()
    }

    @Test fun defaultsOnAndPersists() {
        resetPrefs()
        assertTrue("banner on by default", AppSettings.homeBanner.value)
        AppSettings.setHomeBanner(false)
        AppSettings.init(RuntimeEnvironment.getApplication())
        assertFalse("off survives a reload", AppSettings.homeBanner.value)
        AppSettings.setHomeBanner(true)
    }

    @Test fun bannerOnShowsTheFeaturedTitle() {
        resetPrefs()
        AppSettings.setHomeBanner(true)
        showHome()
        // The hero block carries the banner's own buttons; the title alone also appears as a row card.
        rule.onNodeWithText("More Info").assertIsDisplayed()
        capture("home-banner-on")
    }

    @Test fun bannerOffHidesTheHeroAndRowsStartOnTop() {
        resetPrefs()
        AppSettings.setHomeBanner(false)
        showHome()
        rule.onAllNodesWithText("More Info").assertCountEquals(0)
        rule.onAllNodesWithText("Play from start").assertCountEquals(0)
        rule.onNodeWithText("Continue Watching").assertIsDisplayed()
        capture("home-banner-off")
        AppSettings.setHomeBanner(true)
    }
}
