package tv.jetflix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.components.ArtImage
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.details.HeaderArtAlignment
import tv.jetflix.app.ui.login.LoginContent
import tv.jetflix.app.ui.login.LoginUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Synthetic art only (drawn in code). Layout renders, not screenshots from a TV. */
class Phase2SnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private fun p(b: Bitmap) = BitmapPainter(b.asImageBitmap())

    /** 16:9 backdrop with labelled thirds and a "face" circle high in the frame. */
    private fun landmarks(): Bitmap {
        val b = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888); val c = Canvas(b); val pt = Paint().apply { textSize = 48f; isAntiAlias = true }
        val cols = intArrayOf(0xFF8B2E2E.toInt(), 0xFF2E7D4F.toInt(), 0xFF2E4F8B.toInt()); val names = arrayOf("TOP third", "MIDDLE third", "BOTTOM third")
        for (i in 0..2) { pt.color = cols[i]; c.drawRect(0f, i * 240f, 1280f, (i + 1) * 240f, pt); pt.color = 0xFFFFFFFF.toInt(); c.drawText(names[i], 40f, i * 240f + 120f, pt) }
        pt.color = 0xFFFFD27F.toInt(); c.drawCircle(900f, 170f, 90f, pt)
        return b
    }

    private fun logo(w: Int, h: Int, label: String): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        val pt = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true; textSize = h * 0.6f; isFakeBoldText = true }
        c.drawText(label, 8f, h * 0.7f, pt); return b
    }

    /** A 1080p TV at tvdpi shows the header box about 1440dp x 460dp (aspect 3.13). Same aspect here, drawn smaller. */
    @Test fun cropBeforeAfter() {
        paparazzi.snapshot(name = "crop_before_after") {
            JetflixTheme {
                Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                    CompositionLocalProvider(LocalTestArtwork provides { p(landmarks()) }) {
                        Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1440f / 460f)) { ArtImage("x", null, Modifier.fillMaxSize()) }
                        Box(Modifier.fillMaxWidth(0.7f).aspectRatio(1440f / 460f)) { ArtImage("x", null, Modifier.fillMaxSize(), alignment = HeaderArtAlignment) }
                    }
                }
            }
        }
    }

    private fun repo() = JellyfinRepository().apply { apiFor("http://demo.test") }
    private fun details(logo: ((String?) -> BitmapPainter?)?, played: Boolean, fav: Boolean, trailer: Boolean = true): @androidx.compose.runtime.Composable () -> Unit = {
        CompositionLocalProvider(LocalTestArtwork provides { u -> if (u?.contains("Images/Logo") == true) logo?.invoke(u) else p(landmarks()) }) {
            JetflixTheme {
                DetailsContent(
                    DetailsUiState(
                        loading = false,
                        item = BaseItemDto(Id = "d1", Name = "Demo Show", Type = "Movie", ProductionYear = 2020, OfficialRating = "TV-MA", Overview = "A synthetic overview line for layout checking only.", Genres = listOf("Drama", "Comedy"), UserData = UserItemDataDto(Played = played, IsFavorite = fav)),
                        trailer = if (trailer) tv.jetflix.app.data.TrailerSource.YouTube("abcdefghijk", "Demo trailer") else null,
                    ),
                    repo(), {}, { _, _ -> }, {}, {}, {},
                )
            }
        }
    }

    @Test fun detailsNoLogoDefaultStates() { paparazzi.snapshot(name = "details_text_title_icons_off") { details(null, false, false)() } }
    @Test fun detailsWideLogoActiveStates() { paparazzi.snapshot(name = "details_wide_logo_icons_on") { details({ p(logo(900, 140, "A WIDE TITLE LOGO")) }, true, true)() } }
    @Test fun detailsTallLogo() { paparazzi.snapshot(name = "details_tall_logo") { details({ p(logo(200, 400, "T")) }, false, true)() } }

    @Test fun loginWithSavedProfiles() {
        paparazzi.snapshot(name = "login_saved_profiles") {
            JetflixTheme {
                LoginContent(LoginUiState(serverUrl = "https://media.test", username = "Alex", profiles = listOf("Alex", "Sam", "Jo", "Kim", "Lee", "Ana"), savedProfiles = listOf("Alex", "Sam")), { _, _, _ -> }, {}, {}, {})
            }
        }
    }

    @Test fun loginHttpWarning() {
        paparazzi.snapshot(name = "login_http_warning") {
            JetflixTheme { LoginContent(LoginUiState(serverUrl = "192.168.1.10:8096"), { _, _, _ -> }, {}, {}, {}) }
        }
    }
}
