package tv.jetflix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.actor.ActorContent
import tv.jetflix.app.ui.actor.ActorUiState
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.theme.JetflixTheme

/** Full-page TV actor-page captures: the life-dates line under the name with artwork present. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ActorDatesCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun synth(url: String?): BitmapPainter? {
        url ?: return null
        val h = url.hashCode()
        val hue = ((h % 360) + 360) % 360
        val (w, ht) = if (url.contains("Backdrop")) 960 to 540 else 200 to 300
        val bmp = Bitmap.createBitmap(w, ht, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), ht.toFloat(), Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.5f, 0.7f)), Color.HSVToColor(floatArrayOf(((hue + 60) % 360).toFloat(), 0.7f, 0.25f)), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), ht.toFloat(), p)
        return BitmapPainter(bmp.asImageBitmap())
    }

    private fun movies(n: Int) = (1..n).map {
        BaseItemDto(Id = "m$it", Name = "Synthetic Title $it", ProductionYear = 2024 - it % 30)
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

    private fun page(state: ActorUiState) {
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { synth(it) }) {
                JetflixTheme {
                    ActorContent(state, imageUrlFor = { "http://demo.test/Items/${it.Id}/Images/Primary" }, onOpenTitle = {})
                }
            }
        }
    }

    @Test fun actorWithLifeDates() {
        page(ActorUiState(loading = false, name = "Marilyn Example", dates = "Born Jun 1, 1926 · Died Aug 4, 1962",
            bio = "A short synthetic biography used only for layout testing. ".repeat(3),
            photoUrl = "http://demo.test/Items/p1/Images/Primary", titles = movies(8)))
        capture("actor-dates-full")
    }

    @Test fun actorBirthOnly() {
        page(ActorUiState(loading = false, name = "Chadwick Example", dates = "Born Nov 29, 1976",
            bio = "A short synthetic biography used only for layout testing. ".repeat(2),
            photoUrl = "http://demo.test/Items/p2/Images/Primary", titles = movies(6)))
        capture("actor-dates-birth-only")
    }
}
