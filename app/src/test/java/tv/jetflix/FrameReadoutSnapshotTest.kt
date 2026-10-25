package tv.jetflix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.components.FrameStatsMath
import tv.jetflix.app.ui.components.FrameResultCard
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Renders of the Settings toggle and the frame readout (layoutlib, not a TV). */
class FrameReadoutSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private fun render(name: String, result: Boolean) {
        val s = FrameStatsMath.summarize(listOf(9, 11, 12, 14, 17, 40).map { it * 1_000_000L }, 60.0)
        paparazzi.snapshot(name = name) {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {})
                    if (result) FrameResultCard(listOf("Frame timing, last 20 seconds") + s.lines())
                }
            }
        }
    }

    @Test fun settingsRow() = render("frame_test_settings", false)
    @Test fun resultCard() = render("frame_test_result", true)
}
