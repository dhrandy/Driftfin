package tv.jetflix

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.settings.SettingsContent
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.update.GhAsset
import tv.jetflix.app.update.ReleaseInfo
import tv.jetflix.app.update.UpdateUi

/** Tall frame so every Settings row shows, including the last one. Layout render, not a TV. */
class SettingsTallSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    /** The bottom of Settings: the last rows and the update row in each state (the full page is too tall for one render). */
    private fun row(u: UpdateUi): @androidx.compose.runtime.Composable () -> Unit = {
        val (v, d) = tv.jetflix.app.ui.settings.updateTexts(u)
        tv.jetflix.app.ui.settings.SettingRow("Check for updates", d, true, {}, valueText = v)
    }

    @Test fun updateRows() = paparazzi.snapshot(name = "settings_update_rows") {
        JetflixTheme {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(tv.jetflix.app.ui.theme.JetflixColors.Background).padding(48.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                androidx.tv.material3.Button(onClick = {}) { androidx.tv.material3.Text("Switch profile") }
                androidx.tv.material3.Button(onClick = {}) { androidx.tv.material3.Text("Sign out and forget this profile") }
                row(UpdateUi.Idle)()
                row(UpdateUi.UpToDate(15))()
                row(UpdateUi.Available(ReleaseInfo(99, GhAsset("jetflix-release.apk"), null)))()
                row(UpdateUi.Downloading)()
                row(UpdateUi.ReadyToInstall(99))()
                row(UpdateUi.Error("The downloaded app is signed by a different certificate. Nothing was installed."))()
            }
        }
    }
}
