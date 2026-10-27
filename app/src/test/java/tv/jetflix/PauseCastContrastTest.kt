package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.player.PauseCastStrip

/** Renders the paused cast strip over a bright frame: names must stay readable on the dark bottom fade. */
class PauseCastContrastTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val people = listOf("Ada Lovelace", "Grace Hopper", "Alan Turing", "Edsger Dijkstra", "Barbara Liskov", "Donald Knuth")
        .mapIndexed { i, n -> BaseItemPerson(Id = "p$i", Name = n, Type = "Actor") }

    /** A fake face per person, drawn with pure Compose primitives (layoutlib cannot create Bitmaps). */
    private fun portraitPainter(hue: Int, skin: Int, hair: Int) = object : androidx.compose.ui.graphics.painter.Painter() {
        override val intrinsicSize = androidx.compose.ui.geometry.Size(112f, 112f)
        override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
            drawRect(Color(hue))
            drawOval(Color(hair), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.07f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.59f))
            drawOval(Color(skin), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.27f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.46f, size.height * 0.55f))
        }
    }

    private val portraits: Map<String, androidx.compose.ui.graphics.painter.Painter> = run {
        val skins = intArrayOf(0xFFF1C27D.toInt(), 0xFFE0AC69.toInt(), 0xFFC68642.toInt(), 0xFFFFDBAC.toInt(), 0xFF8D5524.toInt(), 0xFFF3D0B0.toInt())
        val backs = intArrayOf(0xFF3A6EA5.toInt(), 0xFFB03A48.toInt(), 0xFF3E8E5A.toInt(), 0xFF7A5EA8.toInt(), 0xFFB57A2E.toInt(), 0xFF4FA3A5.toInt())
        people.mapIndexed { i, p ->
            "http://demo.test/Items/" + p.Id + "/Images/Primary" to portraitPainter(backs[i], skins[i], 0xFF2B2B2B.toInt())
        }.toMap()
    }

    @Test fun castStripOnBrightFrame() {
        paparazzi.snapshot(name = "pause_cast_bright_frame") {
            tv.jetflix.app.ui.theme.JetflixTheme {
            androidx.compose.runtime.CompositionLocalProvider(tv.jetflix.app.ui.components.LocalTestArtwork provides { url: String? -> url?.let { portraits[it] } }) {
                Box(Modifier.fillMaxSize().background(Color(0xFFD8DDE4))) {
                    // Same scrim PlayerScreen draws behind the strip.
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp).background(tv.jetflix.app.ui.player.PauseCastScrim),
                    )
                    PauseCastStrip(
                        cast = people, selected = 1, imageUrlFor = { "http://demo.test/Items/" + it.Id + "/Images/Primary" },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                    )
                }
            }
            }
        }
    }
}
