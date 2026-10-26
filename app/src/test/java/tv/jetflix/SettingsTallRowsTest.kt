package tv.jetflix

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Tall Settings render so every row, including the new HDR and cast ones, is visible. */
class SettingsTallRowsTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 2400, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun allRows() = paparazzi.snapshot {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, pauseCast = true, formatBadges = true) }
    }
}
