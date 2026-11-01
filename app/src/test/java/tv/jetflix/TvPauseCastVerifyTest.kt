package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemPerson
import tv.jetflix.app.ui.player.PauseCastStrip
import tv.jetflix.app.ui.player.PlayerOverlay
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * Additive verification for the pause-cast strip and pause air-date line (patch 07d5b820 +
 * 06a9cf44): every card browsable to the very last one, the hint's "OK movies" only on openable
 * (real Jellyfin) people, and pixel captures of the dimmed display-only cards, the long episode
 * line with air date.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TvPauseCastVerifyTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val out = File("build/outputs/qa/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("QA_CAPTURE $name ${out.length()}B")
    }

    /** 7 real Jellyfin people, then 8 TVMaze display-only people (Id null). */
    private fun mixedCast() = (1..15).map { i ->
        BaseItemPerson(
            Id = if (i <= 7) "p$i" else null,
            Name = "Person %02d".format(i),
            Role = "Character $i",
            Type = if (i % 3 == 0) "GuestStar" else "Actor",
        )
    }

    private fun strip(cast: List<BaseItemPerson>, selected: Int?) {
        rule.setContent { JetflixTheme { PauseCastStrip(cast = cast, selected = selected, imageUrlFor = { null }) } }
        rule.waitForIdle()
    }

    @Test fun lastCastCardIsReachableAndOnScreen() {
        strip(mixedCast(), 14)
        rule.onNodeWithText("Person 15").assertIsDisplayed()
    }

    @Test fun hintWithoutSelectionOnlyOffersBrowse() {
        strip(mixedCast(), null)
        rule.onNodeWithText("Cast   ▼ to browse").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("OK movies", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun hintOnOpenablePersonOffersOkMovies() {
        strip(mixedCast(), 2)
        rule.onNodeWithText("OK movies", substring = true).assertIsDisplayed()
    }

    @Test fun hintOnDisplayOnlyPersonOmitsOkMovies() {
        strip(mixedCast(), 10)
        rule.onNodeWithText("Cast   ◀ ▶ choose   ▲ back").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("OK movies", substring = true).fetchSemanticsNodes().size)
    }

    @Test fun capturePausedOverlayMixedCastDimmedCardSelected() {
        val ep = BaseItemDto(
            Type = "Episode", SeriesName = "Jury Duty", ParentIndexNumber = 1, IndexNumber = 9,
            Name = "Behind the Scenes of Company Retreat: A Very Long Episode Title That Keeps Going",
            PremiereDate = "2026-04-09T00:00:00Z",
        )
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerOverlay(
                        visible = true, item = ep, isPlaying = false,
                        positionTicks = 60_000_000L, bufferedTicks = 60_000_000L, durationTicks = 600_000_000L,
                        castVisible = true, cast = mixedCast(), castSelected = 10, personImageUrl = { null },
                    )
                }
            }
        }
        capture("pause-cast-display-only-selected")
        rule.onNodeWithText("Cast   ◀ ▶ choose   ▲ back").assertIsDisplayed()
    }

    @Test fun capturePausedOverlayOpenableSelected() {
        val ep = BaseItemDto(
            Type = "Episode", SeriesName = "Jury Duty", ParentIndexNumber = 1, IndexNumber = 9,
            Name = "Behind the Scenes of Company Retreat", PremiereDate = "2026-04-09T00:00:00Z",
        )
        rule.setContent {
            JetflixTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerOverlay(
                        visible = true, item = ep, isPlaying = false,
                        positionTicks = 60_000_000L, bufferedTicks = 60_000_000L, durationTicks = 600_000_000L,
                        castVisible = true, cast = mixedCast(), castSelected = 2, personImageUrl = { null },
                    )
                }
            }
        }
        capture("pause-cast-openable-selected")
        rule.onNodeWithText("OK movies", substring = true).assertIsDisplayed()
    }
}
