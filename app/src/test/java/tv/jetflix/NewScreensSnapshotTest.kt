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
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.settings.SettingsContent

/** UI renders (layoutlib, not a running TV) of Settings, Login with profiles, and Details with My List on. */
class NewScreensSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    private fun art(url: String?): androidx.compose.ui.graphics.painter.BitmapPainter? {
        val m = Regex("/Items/([0-9a-f]+)/Images/(Primary|Backdrop)").find(url ?: return null) ?: return null
        val bytes = javaClass.classLoader!!.getResourceAsStream("regression-art/${m.groupValues[1]}_${m.groupValues[2]}.jpg")?.readBytes() ?: return null
        return androidx.compose.ui.graphics.painter.BitmapPainter(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap())
    }

    @Test fun settings() {
        paparazzi.snapshot(name = "settings") {
            tv.jetflix.app.ui.theme.JetflixTheme {
                SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun loginProfiles() {
        paparazzi.snapshot(name = "login_profiles") {
            tv.jetflix.app.ui.theme.JetflixTheme {
                LoginContent(LoginUiState(serverUrl = "http://192.168.1.10:8096", username = "alex", profiles = listOf("alex", "guest", "kids")), { _, _, _ -> }, {})
            }
        }
    }

    @Test fun detailsInMyList() {
        val movie = items.first { it.Name == "The General" }.let { it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = true)) }
        paparazzi.snapshot(name = "details_mylist") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    DetailsContent(DetailsUiState(loading = false, item = movie, playTarget = movie, similar = items.take(8)), JellyfinRepository().apply { apiFor("http://demo.test") }, {}, { _, _ -> }, {}, {}, {})
                }
            }
        }
    }

    @Test fun detailsWith4kBadge() {
        val movie = items.first { it.Name == "The General" }
        paparazzi.snapshot(name = "details_4k_badge") {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme {
                    DetailsContent(DetailsUiState(loading = false, item = movie, playTarget = movie, similar = items.take(8), is4k = true), JellyfinRepository().apply { apiFor("http://demo.test") }, {}, { _, _ -> }, {}, {}, {})
                }
            }
        }
    }
}
