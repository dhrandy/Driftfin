package tv.jetflix

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.settings.SettingsCategory
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme

/** Each Settings rail group renders in its own right-hand pane; snapshot the panes most likely to overflow. */
class SettingsPaneSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun updatesPane() = paparazzi.snapshot(name = "settings_pane_updates") {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, pauseCast = true, formatBadges = true, buildLabel = "Driftfin TV build 45") }
    }

    @Test fun appearancePane() = paparazzi.snapshot(name = "settings_pane_appearance") {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, pauseCast = true, formatBadges = true, initialCategory = SettingsCategory.Appearance, buildLabel = "Driftfin TV build 45") }
    }

    @Test fun homePane() = paparazzi.snapshot(name = "settings_pane_home") {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, initialCategory = SettingsCategory.HomeDiscovery, buildLabel = "Driftfin TV build 45") }
    }

    @Test fun accountPane() = paparazzi.snapshot(name = "settings_pane_account") {
        JetflixTheme { SettingsContent(true, true, false, "demo", "http://demo.test", {}, {}, {}, {}, {}, initialCategory = SettingsCategory.Account, buildLabel = "Driftfin TV build 45") }
    }
}
