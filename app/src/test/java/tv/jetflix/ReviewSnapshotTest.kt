package tv.jetflix

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.home.*
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.settings.SettingsContent

/** Build 10 review renders (layoutlib, not a TV). */
class ReviewSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun art(url: String?): androidx.compose.ui.graphics.painter.BitmapPainter? {
        val m = Regex("/Items/([0-9a-f]+)/Images/(Primary|Backdrop)").find(url ?: return null) ?: return null
        val bytes = javaClass.classLoader!!.getResourceAsStream("art/${m.groupValues[1]}_${m.groupValues[2]}.jpg")?.readBytes() ?: return null
        var bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        // Mimic the server's maxWidth resize, so small blur copies really are small, as they are on the device.
        val w = Regex("maxWidth=(\\d+)").find(url)?.groupValues?.get(1)?.toInt()
        if (w != null && w < bmp.width) bmp = android.graphics.Bitmap.createScaledBitmap(bmp, w, (bmp.height * w.toFloat() / bmp.width).toInt().coerceAtLeast(1), true)
        return androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap(), filterQuality = androidx.compose.ui.graphics.FilterQuality.Low)
    }

    private val repo get() = JellyfinRepository().apply { apiFor("http://demo.test") }

    @Test fun screensaverCaption() {
        val m = items.first { it.Name == "Sahara" }
        paparazzi.snapshot(name = "review_screensaver") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.components.ScreensaverOverlay(listOf(tv.jetflix.app.ui.components.ScreensaverSlide(repo.backdropUrl(m, 1920)!!, m.displayTitle(), m.Overview ?: "A long description goes here so the three line limit and the gradient can be checked against a bright picture. ".repeat(3))))
            }
        }
    }

    @Test fun screensaverCaptionLongText() {
        val m = items.first { it.Name == "Sahara" }
        val longOverview = "A retired sergeant, a runaway scientist and a crew of mercenaries cross a burning desert to recover a stolen archive before the border closes, and each of them has a reason to keep it hidden. ".repeat(3)
        paparazzi.snapshot(name = "review_screensaver_long") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.components.ScreensaverOverlay(listOf(tv.jetflix.app.ui.components.ScreensaverSlide(repo.backdropUrl(m, 1920)!!, "The Extraordinarily Long Title of a Film: Part Two, The Reckoning", longOverview)))
            }
        }
    }

    @Test fun libraryPicker() {
        paparazzi.snapshot(name = "review_library_picker") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    tv.jetflix.app.ui.library.LibraryContent(
                        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = items, banner = items.first(), libraries = listOf("a" to "Movies", "b" to "Kids Movies")),
                        repo, tv.jetflix.app.ui.components.JetflixTab.MOVIES, "demo", {}, {}, {},
                    )
                }
            }
        }
    }

    @Test fun homeTop() {
        val sections = listOf(HomeSection("Recently Added Movies", items.take(8), wide = false), HomeSection("Kids Movies", items.drop(3).take(8), wide = false))
        paparazzi.snapshot(name = "review_home_top") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    HomeContent(HomeUiState(loading = false, hero = items.first().copy(UserData = null), heroes = emptyList(), sections = sections), repo, "demo", {}, {}, {}, {})
                }
            }
        }
    }
}
