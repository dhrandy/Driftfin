package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Randy on build 39, The Great: the episode is named twice ("S2:E1 · Aired ..." above a "Play S2:E1" button)
 * and the standalone year repeats the premiere date's year. The button is a plain Play/Resume when the line
 * above names the episode, and the year is dropped when the premiere date is shown. Renders the real header.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
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

    @Test fun seriesHeaderNamesTheEpisodeAndTheDateOnce() {
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

        // The episode air line stays, naming the episode once above the button.
        rule.onAllNodes(hasText("S2:E1")).assertCountEquals(1)
        rule.onAllNodes(hasText("Aired Nov 19, 2021")).assertCountEquals(1)
        // The button is a plain Play: no "Play S2:E1" doubling the label above it.
        rule.onAllNodes(hasText("Play S2:E1")).assertCountEquals(0)
        rule.onAllNodes(hasText("Play")).assertCountEquals(1)
        // The premiere date stays; the standalone year duplicating it is gone.
        rule.onAllNodes(hasText("Premiered May 15, 2020")).assertCountEquals(1)
        rule.onAllNodes(hasText("2020")).assertCountEquals(0)

        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(b))
        val f = File("build/outputs/qa/details-header-dedup.png"); f.parentFile.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CAPTURE ${f.absolutePath}")
    }
}
