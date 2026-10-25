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
class MorningSnapshotTest {
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

    private val marked get() = items.mapIndexed { i, it ->
        when (i % 4) { 0 -> it.copy(UserData = UserItemDataDto(Played = true)); 1 -> it.copy(UserData = UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 40.0)); else -> it }
    }
    private fun lib(name: String, tab: tv.jetflix.app.ui.components.JetflixTab, st: tv.jetflix.app.ui.library.LibraryUiState, title: String? = null) =
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    tv.jetflix.app.ui.library.LibraryContent(st, JellyfinRepository().apply { apiFor("http://demo.test") }, tab, "demo", {}, {}, {}, collectionTitle = title)
                }
            }
        }

    @Test fun moviesBannerBadges() = lib("movies_banner_badges", tv.jetflix.app.ui.components.JetflixTab.MOVIES,
        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = marked, banner = items.first { it.Name == "Sahara" }.copy(Overview = "A desert adventure about an unlikely crew.", UserData = null)))

    @Test fun myJetflixCollections() = lib("my_jetflix", tv.jetflix.app.ui.components.JetflixTab.MY_LIST,
        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = marked.take(8).mapIndexed { i, it -> it.copy(Type = "BoxSet", Name = (it.Name ?: "") + " Collection", ChildCount = 3 + i, UserData = null) }, section = tv.jetflix.app.ui.library.MySection.Collections, reversed = true))

    @Test fun posterSizeLarge() {
        tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Large)
        lib("poster_large", tv.jetflix.app.ui.components.JetflixTab.MOVIES, tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = marked))
        tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Medium)
    }

    @Test fun posterSizeSmall() {
        tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Small)
        lib("poster_small", tv.jetflix.app.ui.components.JetflixTab.MOVIES, tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = marked))
        tv.jetflix.app.data.AppSettings.setPosterSize(tv.jetflix.app.data.PosterSize.Medium)
    }

    @Test fun homeInsetHero() {
        paparazzi.snapshot(name = "home_inset_hero") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    tv.jetflix.app.ui.home.HomeContent(
                        tv.jetflix.app.ui.home.HomeUiState(
                            loading = false, hero = items.first { it.Name == "Jungle Book" }.copy(UserData = null), heroes = emptyList(),
                            sections = listOf(tv.jetflix.app.ui.home.HomeSection("Popular Picks", marked, wide = false)),
                        ),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {},
                    )
                }
            }
        }
    }

    @Test fun openedCollection() = lib("collection_open", tv.jetflix.app.ui.components.JetflixTab.MY_LIST,
        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = marked.take(5)), title = "Desert Adventures Collection")

    @Test fun homeRowOrder() {
        val order = tv.jetflix.app.ui.home.orderHomeSections(
            items.take(2), items.take(2), items.take(3), items.take(4), items.take(2),
            tv.jetflix.app.ui.home.HomeSection("More like X", items.take(2), wide = false),
            listOf(tv.jetflix.app.ui.home.HomeSection("Action", items.take(2), wide = false)),
        )
        org.junit.Assert.assertEquals(
            listOf("Continue Watching", "Next Up", "Recently Added Movies", "Favorites", "Recently Added Episodes", "Action", "More like X"),
            order.map { it.title },
        )
        paparazzi.snapshot(name = "home_row_order") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    tv.jetflix.app.ui.home.HomeContent(
                        tv.jetflix.app.ui.home.HomeUiState(loading = false, hero = items.first { it.Name == "Jungle Book" }.copy(UserData = null), heroes = emptyList(), sections = order),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, previewScrollIndex = 1,
                    )
                }
            }
        }
    }

    @Test fun libraryFilters() = lib("library_filters", tv.jetflix.app.ui.components.JetflixTab.MOVIES,
        tv.jetflix.app.ui.library.LibraryUiState(loading = false, items = items.map { it.copy(UserData = null) }, sort = tv.jetflix.app.ui.library.LibrarySort.PlayCount, watch = tv.jetflix.app.ui.library.WatchFilter.Unwatched, genre = "Drama"))
}
