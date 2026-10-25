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
class TrailerSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 2300, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("demo-items.json")!!.readText(),
        ).Items

    private fun repo() = JellyfinRepository().apply { apiFor("http://demo.test") }

    @Test fun detailsTrailerButton() {
        val m = items.first { it.isMovie }
        paparazzi.snapshot(name = "details_trailer_button") {
            tv.jetflix.app.ui.theme.JetflixTheme {
                DetailsContent(
                    DetailsUiState(loading = false, item = m, playTarget = m, similar = items.take(8),
                        trailer = tv.jetflix.app.data.TrailerSource.YouTube("dQw4w9WgXcQ", "Trailer")),
                    repo(), {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
    }

    @Test fun trailerError() {
        paparazzi.snapshot(name = "trailer_error") {
            tv.jetflix.app.ui.theme.JetflixTheme { tv.jetflix.app.ui.player.TrailerErrorOverlay("This trailer can't be played in the app.") }
        }
    }
}
