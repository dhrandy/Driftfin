package tv.jetflix

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState

/**
 * UI renders of the real Home screen with real poster and backdrop art from the public
 * Jellyfin demo server. These are layoutlib renders, not screenshots from a running TV.
 */
class HomeContinueWatchingPostersTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1700, orientation = com.android.resources.ScreenOrientation.LANDSCAPE, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH),
        theme = "android:Theme.Material.NoActionBar",
        environment = app.cash.paparazzi.detectEnvironment().copy(compileSdkVersion = 34),
    )

    private val items: List<BaseItemDto> = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(
            javaClass.classLoader!!.getResource("demo-items.json")!!.readText(),
        ).Items

    /** Synthetic artwork drawn in code (no files, nothing from a real server): posters are tall, scenes are wide. */
    private fun art(url: String?): BitmapPainter? {
        val m = Regex("/Items/([0-9a-f]+)/Images/(Primary|Backdrop)").find(url ?: return null) ?: return null
        val poster = m.groupValues[2] == "Primary"
        val (w, h) = if (poster) 342 to 513 else 960 to 540
        val hue = ((m.groupValues[1].hashCode() and 0xff) * 360f / 256f + if (poster) 0f else 180f) % 360f
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint().apply { isAntiAlias = true }
        p.color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.7f)); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = android.graphics.Color.WHITE; p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 8f
        c.drawRect(8f, 8f, w - 8f, h - 8f, p)
        c.drawLine(0f, 0f, w.toFloat(), h.toFloat(), p); c.drawLine(w.toFloat(), 0f, 0f, h.toFloat(), p)
        p.style = android.graphics.Paint.Style.FILL; p.textSize = if (poster) 48f else 72f
        c.drawText(if (poster) "POSTER" else "SCENE", w / 8f, h / 2f, p)
        return BitmapPainter(bmp.asImageBitmap())
    }

    @Test fun posters() {
        tv.jetflix.app.data.AppSettings.setContinueWatchingPosters(true)
        try { render("home_continue_posters") } finally { tv.jetflix.app.data.AppSettings.setContinueWatchingPosters(false) }
    }

    @Test fun scenes() = render("home_continue_scenes")

    private fun render(name: String) {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        val hero = items.first { it.Name == "Jungle Book" }
        val state = HomeUiState(
            loading = false,
            hero = hero,
            sections = listOf(
                HomeSection("Continue Watching", items.take(6).map { it.copy(UserData = tv.jetflix.app.data.UserItemDataDto(PlaybackPositionTicks = 1, PlayedPercentage = 35.0)) }, wide = true),
                HomeSection("Recently Added Movies", items.drop(2), wide = false),
                HomeSection("Popular Picks", items.reversed(), wide = false),
            ),
        )
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(LocalTestArtwork provides { art(it) }) {
                tv.jetflix.app.ui.theme.JetflixTheme { HomeContent(state, repo, "demo", {}, {}, {}, {}) }
            }
        }
    }
}
