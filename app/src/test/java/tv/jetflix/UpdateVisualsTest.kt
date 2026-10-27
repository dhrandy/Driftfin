package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.UpdateNoticeBadge
import tv.jetflix.app.update.WhatsNewDialog

/** Pixel renders of the What's new dialog and the startup update notice. */
class UpdateVisualsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun whatsNewDialog() {
        paparazzi.snapshot(name = "whats_new_dialog") {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {
                    WhatsNewDialog(
                        notes = listOf(
                            "Home: focus no longer gets trapped",
                            "Paused cast strip is easier to read on bright frames",
                            "Check for updates moved to the top of Settings",
                        ),
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test fun startupUpdateNotice() {
        paparazzi.snapshot(name = "startup_update_notice") {
            JetflixTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {
                    UpdateNoticeBadge(build = 19, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
                }
            }
        }
    }
}
