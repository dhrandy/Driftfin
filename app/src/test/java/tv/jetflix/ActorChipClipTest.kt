package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.details.CastChip
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixTheme
import tv.jetflix.app.ui.theme.JetflixType

/** Search's Actors row: the focused chip's zoom and outline must never clip at the row's edges, and
 *  a long name must ellipsize INSIDE the card, centered under the photo. Captures for pixel review. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ActorChipClipTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        val f = File("build/outputs/qa/$name.png")
        f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val people = listOf("Hugo Weaving", "Jacki Weaver", "Samara Weaver", "Sigourney Weaver")

    private fun row() {
        // Same structure and padding as SearchScreen's Actors section.
        rule.setContent {
            JetflixTheme {
                Column(Modifier.background(JetflixColors.Background).padding(48.dp)) {
                    Text("Actors", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                        people.forEach { p ->
                            item(key = p) {
                                CastChip(person = BaseItemPerson(Name = p, Id = p), imageUrl = null)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun firstChipFocusedKeepsItsLeftOutline() {
        row()
        rule.onNodeWithText("Hugo Weaving").requestFocus()
        capture("actorchip-first-focused")
    }

    @Test fun lastChipFocusedEllipsizesInsideTheCard() {
        row()
        rule.onNodeWithText("Sigourney Weaver", substring = true).requestFocus()
        capture("actorchip-last-focused")
    }
}
