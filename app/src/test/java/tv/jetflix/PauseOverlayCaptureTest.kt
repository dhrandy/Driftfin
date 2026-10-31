package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.player.PlayerOverlay
import tv.jetflix.app.ui.theme.JetflixTheme

/** The paused player overlay carries the item's summary and the "Up next" episode name as quiet text,
 *  with the existing title, pause icon, cast strip and bottom bar untouched. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PauseOverlayCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val episode = BaseItemDto(
        Id = "e5", Type = "Episode", SeriesName = "Demo Series", Name = "The Long Road",
        ParentIndexNumber = 2, IndexNumber = 5, RunTimeTicks = 45L * 600_000_000,
        Overview = "A marshal tracks a fugitive across the territory while holding his family together, " +
            "as an old case resurfaces and loyalties shift in the small border town he swore to protect.",
    )
    private val next = BaseItemDto(Id = "e6", Type = "Episode", SeriesName = "Demo Series", Name = "Back to Town",
        ParentIndexNumber = 2, IndexNumber = 6, RunTimeTicks = 44L * 600_000_000)
    private val cast = listOf(
        BaseItemPerson(Id = "p1", Name = "Pat Example", Role = "Lead", Type = "Actor"),
        BaseItemPerson(Id = "p2", Name = "Riley Sample", Role = "Sidekick", Type = "Actor"),
        BaseItemPerson(Id = "p3", Name = "Jordan Demo", Role = "Marshal", Type = "Actor"),
    )

    private fun capture(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        val f = File("build/outputs/qa/$name.png")
        f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(playing: Boolean, backdrop: Color, infoVisible: Boolean = true) {
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { _ -> null }) {
                JetflixTheme {
                    // The overlay always floats over real video; bright and dark stand-ins check readability.
                    Box(Modifier.fillMaxSize().background(backdrop)) {
                        PlayerOverlay(
                            visible = true, item = episode, isPlaying = playing,
                            positionTicks = 12L * 600_000_000, bufferedTicks = 15L * 600_000_000,
                            durationTicks = episode.RunTimeTicks ?: 0L,
                            nextEpisode = next,
                            infoVisible = infoVisible,
                            castVisible = !playing, cast = if (playing) emptyList() else cast, castSelected = null,
                            personImageUrl = { null },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun pausedOverlayShowsSummaryAndUpNextOnADarkScene() {
        show(playing = false, backdrop = Color(0xFF14161C))
        rule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        rule.onNodeWithText("A marshal tracks a fugitive", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Up next: S2:E6 · Back to Town").assertIsDisplayed()
        rule.onNodeWithText("Pat Example", substring = true).assertIsDisplayed()
        capture("pause-summary-upnext")
    }

    @Test fun pausedOverlayStaysReadableOnABrightScene() {
        show(playing = false, backdrop = Color(0xFFC9CDD4))
        rule.onNodeWithText("A marshal tracks a fugitive", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Up next: S2:E6 · Back to Town").assertIsDisplayed()
        capture("pause-summary-upnext-bright")
    }

    @Test fun pausedOverlayHidesInfoWhenSettingOff() {
        // Randy's Settings toggle: summary + Up next hidden, title, pause icon and cast strip stay.
        show(playing = false, backdrop = Color(0xFF14161C), infoVisible = false)
        rule.onNodeWithContentDescription("Paused").assertIsDisplayed()
        rule.onNodeWithText("A marshal tracks a fugitive", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Up next: S2:E6 · Back to Town").assertDoesNotExist()
        rule.onNodeWithText("Pat Example", substring = true).assertIsDisplayed()
    }

    @Test fun playingOverlayHidesSummaryAndUpNext() {
        show(playing = true, backdrop = Color(0xFF14161C))
        rule.onNodeWithText("A marshal tracks a fugitive", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Up next: S2:E6 · Back to Town").assertDoesNotExist()
    }
}
