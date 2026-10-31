package tv.jetflix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Full Search screen with artwork loaded: actor chips + poster results, first actor focused,
 *  so the chip outline, photo and name centering are reviewed in the real page layout. */
@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchFullScreenCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun synth(url: String?): BitmapPainter? {
        url ?: return null
        val h = url.hashCode()
        val hue = ((h % 360) + 360) % 360
        val person = url.contains("/p")
        val (w, ht) = if (person) 200 to 200 else 200 to 300
        val bmp = Bitmap.createBitmap(w, ht, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), ht.toFloat(), Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.5f, 0.7f)), Color.HSVToColor(floatArrayOf(((hue + 60) % 360).toFloat(), 0.7f, 0.25f)), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), ht.toFloat(), p)
        if (person) {
            val q = Paint().apply { color = Color.argb(160, 255, 255, 255) }
            c.drawCircle(w / 2f, ht * 0.4f, ht * 0.18f, q)
            c.drawCircle(w / 2f, ht * 1.0f, ht * 0.4f, q)
        }
        return BitmapPainter(bmp.asImageBitmap())
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        val f = File("build/outputs/qa/$name.png")
        f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun page(focusDown: Boolean = false) {
        val people = listOf("Hugo Example", "Jacki Sample", "Samara Demo", "Sigourney Test")
            .mapIndexed { i, n -> BaseItemDto(Id = "p$i", Name = n, Type = "Person", ImageTags = mapOf("Primary" to "tag$i")) }
        val results = (1..6).map { BaseItemDto(Id = "m$it", Name = "Synthetic Movie $it", Type = "Movie", ProductionYear = 2020 + it, ImageTags = mapOf("Primary" to "mtag$it")) }
        var fm: androidx.compose.ui.focus.FocusManager? = null
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { synth(it) }) {
                JetflixTheme {
                    fm = androidx.compose.ui.platform.LocalFocusManager.current
                    SearchContent(
                        SearchUiState(query = "example", people = people, results = results),
                        JellyfinRepository().apply { apiFor("http://demo.test") },
                        "demo", {}, {}, {}, {}, {}, onActorClick = { _, _ -> })
                }
            }
        }
        if (focusDown) {
            rule.runOnUiThread { fm!!.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
        }
        rule.waitForIdle()
    }

    @Test fun fullScreenWithArtwork() { page(); capture("search-full-artwork") }

    @Test fun fullScreenFirstActorFocused() { page(focusDown = true); capture("search-full-actor-focused") }
}
