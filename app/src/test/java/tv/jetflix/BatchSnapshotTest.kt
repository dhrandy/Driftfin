package tv.jetflix

import androidx.compose.foundation.layout.fillMaxSize
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
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.settings.SettingsContent

/** UI renders (layoutlib, not a running TV) of Settings, Login with profiles, and Details with My List on. */
class BatchSnapshotTest {
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
        return androidx.compose.ui.graphics.painter.BitmapPainter(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap())
    }

    @Test fun librarySortRow() {
        paparazzi.snapshot(name = "library_sort_row") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    tv.jetflix.app.ui.library.LibraryContent(
                        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = items, sort = tv.jetflix.app.ui.library.LibrarySort.Rating),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, tv.jetflix.app.ui.components.JetflixTab.MOVIES, "demo", {}, {}, {},
                    )
                }
            }
        }
    }

    @Test fun focusedDetailsStrip() {
        val item = items.first { it.Name == "Sahara" }.copy(Overview = "A desert adventure about an unlikely crew crossing the dunes to find a lost treasure.", Genres = listOf("Adventure", "Action"), OfficialRating = "PG-13")
        paparazzi.snapshot(name = "focused_details_strip") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        tv.jetflix.app.ui.home.HomeContent(
                            tv.jetflix.app.ui.home.HomeUiState(
                                loading = false, hero = items.first { it.Name == "Jungle Book" }, heroes = emptyList(),
                                sections = listOf(
                                    tv.jetflix.app.ui.home.HomeSection("Continue Watching", items.take(6).map { it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }, wide = true),
                                    tv.jetflix.app.ui.home.HomeSection("Popular Picks", items.reversed(), wide = false),
                                ),
                            ),
                            JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {},
                            restoredFocusKey = "row|x", previewFocusedItem = item, previewScrollIndex = 1,
                        )
                    }
                }
            }
        }
    }
}
