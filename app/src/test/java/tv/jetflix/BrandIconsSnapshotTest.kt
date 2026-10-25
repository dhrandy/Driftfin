package tv.jetflix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** Renders the vector banner and the launcher foreground on its background. layoutlib render, not a launcher screenshot. */
class BrandIconsSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1000, screenHeight = 400, orientation = com.android.resources.ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    @Test fun brand_banner() {
        paparazzi.snapshot {
            Image(painterResource(tv.jetflix.app.R.drawable.banner_driftfin), null, Modifier.size(320.dp, 180.dp))
        }
    }

    @Test fun brand_icon() {
        // Adaptive icon: foreground on #171717, shown through a circle mask (Google TV round) and a rounded-square mask.
        paparazzi.snapshot {
            Row {
                listOf(androidx.compose.foundation.shape.CircleShape, RoundedCornerShape(22.dp)).forEach { shape ->
                    Box(Modifier.padding(8.dp).size(120.dp).clip(shape).background(Color(0xFF171717))) {
                        // Adaptive foreground is 108dp wide with a 66dp safe zone: scaled here to 200dp.
                        Image(painterResource(tv.jetflix.app.R.drawable.ic_launcher_fg), null, Modifier.size(120.dp))
                    }
                }
            }
        }
    }
}
