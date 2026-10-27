package tv.jetflix

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.components.StartupScreen

/** Render the actual bundled asset through the production composable, not a fabricated UI screenshot. */
class StartupScreenSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080,
            orientation = com.android.resources.ScreenOrientation.LANDSCAPE,
            xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )
    @Test fun startupWall() = paparazzi.snapshot(name = "startup_wall_1080p") {
        tv.jetflix.app.ui.theme.JetflixTheme { StartupScreen() }
    }
}
