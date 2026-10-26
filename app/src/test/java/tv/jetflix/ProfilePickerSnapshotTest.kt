package tv.jetflix

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.ui.components.ProfilePickerContent

/** Profile-picker render (layoutlib, not a TV). */
class ProfilePickerSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun profilePicker() {
        paparazzi.snapshot(name = "profile_picker") {
            ProfilePickerContent(
                profiles = listOf("Alex", "Sam"),
                currentName = "Sam",
                connecting = false,
                error = null,
                onSelect = {},
                onSignOut = {},
            )
        }
    }

    @Test fun profilePickerError() {
        paparazzi.snapshot(name = "profile_picker_error") {
            ProfilePickerContent(
                profiles = listOf("Alex", "Sam"),
                currentName = "Sam",
                connecting = true,
                error = "The saved sign-in for Alex expired.",
                onSelect = {},
                onSignOut = {},
            )
        }
    }
}
