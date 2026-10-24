package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.player.AudioSubtitlePanel
import tv.jetflix.app.ui.player.TrackCycler

/** UI render of the Audio & Subtitles panel over a plain dark backdrop (not a running player). */
class PlayerPanelSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun panel() {
        val opts = listOf(
            TrackCycler.Option(C.TRACK_TYPE_AUDIO, "English (AC3 5.1)", true, null, 0),
            TrackCycler.Option(C.TRACK_TYPE_AUDIO, "Spanish (AAC stereo)", false, null, 1),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "Off", false, null, -1),
            TrackCycler.Option(C.TRACK_TYPE_TEXT, "English (SRT)", true, null, 0),
        )
        paparazzi.snapshot(name = "player_panel") {
            tv.jetflix.app.ui.theme.JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF223344))) {
                    AudioSubtitlePanel(opts, 1, Modifier.align(androidx.compose.ui.Alignment.CenterEnd))
                }
            }
        }
    }
}
