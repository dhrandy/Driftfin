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
class EpisodesSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 2300, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
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

    @Test fun detailsSeries() {
        val series = items.first { it.isSeries }
        val eps = items.filter { it.isMovie }.take(5).mapIndexed { i, m ->
            m.copy(Overview = "Episode summary text from the server shows here on two lines when it is long enough to wrap across the row.", Type = "Episode", SeriesName = series.Name, ParentIndexNumber = 1, IndexNumber = i + 1, SeriesId = series.Id)
        }
        paparazzi.snapshot(name = "details_series") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    DetailsContent(
                        DetailsUiState(loading = false, item = series, playTarget = eps[0].copy(ParentIndexNumber = 2, IndexNumber = 5, UserData = tv.jetflix.app.data.UserItemDataDto(PlaybackPositionTicks = 9_000_000_000L, PlayedPercentage = 40.0)), episodes = eps,
                            seasons = listOf(BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season"), BaseItemDto(Id = "s2", Name = "Season 2", Type = "Season")),
                            selectedSeasonId = "s1", similar = items.take(8)),
                        repo(), {}, { _, _ -> }, {}, {}, {},
                    )
                }
            }
        }
    }

    @Test fun detailsSeriesWith4kEpisodes() {
        val series = items.first { it.isSeries }
        val eps = items.filter { it.isMovie }.take(5).mapIndexed { i, m ->
            m.copy(Overview = "Episode summary text from the server shows here on two lines when it is long enough to wrap across the row.", Type = "Episode", SeriesName = series.Name, ParentIndexNumber = 1, IndexNumber = i + 1, SeriesId = series.Id)
        }
        paparazzi.snapshot(name = "details_series_4k_episodes") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    DetailsContent(
                        DetailsUiState(loading = false, item = series, playTarget = eps[0], episodes = eps,
                            seasons = listOf(BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season")),
                            selectedSeasonId = "s1", similar = items.take(8), fourKEpisodeIds = setOf(eps[1].Id, eps[3].Id)),
                        repo(), {}, { _, _ -> }, {}, {}, {},
                    )
                }
            }
        }
    }
}
