package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * The header layout Randy settled on after build 40: the episode is named once, in the Play/Resume
 * button; its air date sits in the header's meta row ("Aired Nov 19, 2021"); the show's premiere date
 * leads the genre line below ("Premiered May 15, 2020 · Drama · ..."); the standalone year still
 * doesn't duplicate a shown date. Renders the real details screen.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
class DetailsHeaderDedupTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val series = BaseItemDto(
        Id = "ser1", Type = "Series", Name = "The Great", ProductionYear = 2020,
        PremiereDate = "2020-05-15T00:00:00.0000000Z", OfficialRating = "TV-MA", ChildCount = 3,
        CommunityRating = 8.1f, Overview = "A genre-bending ride.", BackdropImageTags = listOf("t"),
        Genres = listOf("Drama", "Comedy", "History"),
    )
    private val ep = BaseItemDto(
        Id = "e1", Type = "Episode", Name = "Heads It's Me", SeriesId = "ser1", SeriesName = "The Great",
        ParentIndexNumber = 2, IndexNumber = 1, PremiereDate = "2021-11-19T00:00:00.0000000Z",
        RunTimeTicks = 52L * 60 * 10_000_000,
    )

    @Test fun seriesHeaderPutsTheAirDateUpTopAndThePremiereDateByTheGenres() {
        val art = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF30425E.toInt()) }
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { url -> if (url?.contains("Backdrop") == true) BitmapPainter(art.asImageBitmap()) else null }) {
                JetflixTheme {
                    DetailsContent(
                        DetailsUiState(loading = false, item = series, playTarget = ep, episodes = listOf(ep),
                            seasons = listOf(
                                BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season"),
                                BaseItemDto(Id = "s2", Name = "Season 2", Type = "Season"),
                            ),
                            selectedSeasonId = "s2"),
                        repo, {}, { _, _ -> }, {}, {}, {},
                    )
                }
            }
        }
        rule.waitForIdle()

        // The header's old combined line is gone; the episode is named once, in the button.
        rule.onAllNodes(hasText("S2:E1 · Aired Nov 19, 2021")).assertCountEquals(0)
        rule.onAllNodes(hasText("Play S2:E1")).assertCountEquals(1)
        // The air date is up top in the meta row...
        rule.onAllNodes(hasText("Aired Nov 19, 2021")).assertCountEquals(1)
        // ...and the premiere date leads the genre line, no longer its own meta segment.
        rule.onAllNodes(hasText("Premiered May 15, 2020")).assertCountEquals(0)
        rule.onAllNodes(hasText("Premiered May 15, 2020  ·  Drama · Comedy · History")).assertCountEquals(1)
        // The standalone year still doesn't duplicate a shown date.
        rule.onAllNodes(hasText("2020")).assertCountEquals(0)

        // Bring the genre line into view so the capture shows both halves of the swap.
        rule.onNode(hasText("Premiered May 15, 2020  ·  Drama · Comedy · History")).performScrollTo()
        rule.waitForIdle()

        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        val f = File("build/outputs/qa/details-header-dedup.png"); f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CAPTURE ${f.absolutePath}")
    }
}
