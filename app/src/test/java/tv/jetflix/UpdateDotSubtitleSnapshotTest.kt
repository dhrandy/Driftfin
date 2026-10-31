package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.After
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.player.AudioSubtitlePanel
import tv.jetflix.app.ui.player.TrackCycler
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.GhAsset
import tv.jetflix.app.update.ReleaseInfo
import tv.jetflix.app.update.UpdateNoticeState

/** Paparazzi renders for the TV46 followup visuals: the update red dot left of the username, and the
 *  subtitle check landing on OK immediately (markSelected path, no panel reopen). */
class UpdateDotSubtitleSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @After fun reset() { UpdateNoticeState.available.value = null }

    @Test fun updateDotLeftOfUsername() {
        UpdateNoticeState.available.value = ReleaseInfo(47, GhAsset("jetflix-release.apk", "https://example.test/x", 1L), null)
        paparazzi.snapshot(name = "topnav_update_dot") {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
                    TopNavBar(JetflixTab.HOME, "Demo", {}, {}, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

    @Test fun subtitleCheckLandsOnOkWithoutReopen() {
        val initial = listOf(
            TrackCycler.Option(C.TRACK_TYPE_AUDIO, "English (AC3 5.1)", true, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "Off", true, null, -1),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "English (SRT)", false, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "Spanish (SRT)", false, null, 1),
        )
        // What PlayerScreen's OK handler now does: re-mark synchronously after apply, same panel instance.
        val marked = TrackCycler.markSelected(initial, initial[2])
        paparazzi.snapshot(name = "subtitle_check_immediate") {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF223344))) {
                    AudioSubtitlePanel(marked, 2, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}
