package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.player.UpNextCard

/** Layout renders of the countdown card over plain stand-ins for video (dark and bright). Not screenshots from a TV. */
class UpNextSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun card() {
        paparazzi.snapshot(name = "up_next_card") {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF8A98B5))))) {
                UpNextCard("S1:E4 \u00B7 The Long Way Home", 4, 0.8f, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 120.dp))
            }
        }
    }

    @Test fun cardLongTitleBrightFrame() {
        paparazzi.snapshot(name = "up_next_card_long_bright") {
            Box(Modifier.fillMaxSize().background(Color(0xFFE8E4D8))) {
                UpNextCard("S12:E24 \u00B7 The One Where the Title Is Much Too Long to Fit on a Single Line", 1, 0.2f, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 120.dp))
            }
        }
    }
}
