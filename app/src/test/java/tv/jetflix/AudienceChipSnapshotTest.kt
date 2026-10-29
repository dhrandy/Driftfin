package tv.jetflix

import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.components.JetflixTab

/**
 * UI renders of the real Home screen with real poster and backdrop art from the public
 * Jellyfin demo server. These are layoutlib renders, not screenshots from a running TV.
 */
class AudienceChipSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1300, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("regression-items.json")!!.readText(),
        ).Items

    private fun art(url: String?): BitmapPainter? {
        val m = Regex("/Items/([0-9a-f]+)/Images/(Primary|Backdrop)").find(url ?: return null) ?: return null
        val bytes = javaClass.classLoader!!.getResourceAsStream("regression-art/${m.groupValues[1]}_${m.groupValues[2]}.jpg")?.readBytes() ?: return null
        return BitmapPainter(BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap())
    }

    private fun repo() = JellyfinRepository().apply { apiFor("http://demo.test") }

    @Test fun chipNoKids() {
        paparazzi.snapshot(name = "audience_chip_nokids") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    LibraryContent(LibraryUiState(loading = false, items = items, kidsAvailable = true, audience = tv.jetflix.app.ui.library.Audience.NoKids), repo(), JetflixTab.MOVIES, "demo", {}, {}, {})
                }
            }
        }
    }
}
