package tv.jetflix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.TrailerSource
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme
import java.io.File

/**
 * The header layout settled on after build 40: the episode is named once, in the Play/Resume
 * button; its air date sits in the header's meta row ("Aired Mar 8, 2026 · 52m"); the show's premiere date
 * leads the genre line below ("Premiered Mar 1, 2026"); the standalone year still doesn't duplicate
 * a shown date. Renders the real details screen.
 *
 * The fixture is Marshals-shaped because that is the page flagged on the user's TV: the logo art is
 * the real MARSHALS logo extracted from his screenshot (wide enough to fill the logo box's full
 * height, the case that put the ink against the screen's top edge), the plot is the show's real
 * three-line description, and the header carries the full control row (Resume, Play from start,
 * trailer, watched, watchlist, favorite). Two captures: the resting page and the scrolled page.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-mdpi")
class DetailsHeaderDedupTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val series = BaseItemDto(
        Id = "ser1", Type = "Series", Name = "Marshals", ProductionYear = 2026,
        PremiereDate = "2026-03-01T00:00:00.0000000Z", OfficialRating = "TV-14", ChildCount = 2,
        CommunityRating = 7.8f, BackdropImageTags = listOf("t"),
        Overview = "With the Yellowstone Ranch behind him, Kayce Dutton joins an elite unit of " +
            "U.S. Marshals, combining his skills as a cowboy and Navy SEAL to bring range justice " +
            "to Montana, where he and his teammates must balance family, duty and the high " +
            "psychological cost that comes with serving as the last line of defense in the " +
            "region's war on crime.",
        Genres = listOf("Drama", "Western"),
        ImageTags = mapOf("Logo" to "logo1"),
    )
    private fun ep(n: Int, resume: Boolean = false) = BaseItemDto(
        Id = "e$n", Type = "Episode", Name = "Episode $n", SeriesId = "ser1", SeriesName = "Marshals",
        ParentIndexNumber = 1, IndexNumber = n,
        PremiereDate = if (n == 2) "2026-03-08T00:00:00.0000000Z" else "2026-03-0${n}T00:00:00.0000000Z",
        RunTimeTicks = 52L * 60 * 10_000_000,
        UserData = if (resume) UserItemDataDto(PlaybackPositionTicks = 123L * 10_000_000) else null,
    )
    private val resumeEp = ep(2, resume = true)

    @Test fun seriesHeaderPutsTheAirDateUpTopAndThePremiereDateByTheGenres() {
        val art = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF30425E.toInt()) }
        // The real MARSHALS logo (extracted from the user's TV photo of this page, white ink on transparency).
        val logo = BitmapFactory.decodeStream(javaClass.classLoader!!.getResourceAsStream("art/marshals-logo.png"))
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            CompositionLocalProvider(LocalTestArtwork provides { url ->
                when {
                    url?.contains("Backdrop") == true -> BitmapPainter(art.asImageBitmap())
                    url?.contains("Logo") == true -> BitmapPainter(logo.asImageBitmap())
                    else -> null
                }
            }) {
                JetflixTheme {
                    DetailsContent(
                        DetailsUiState(loading = false, item = series, playTarget = resumeEp,
                            episodes = (1..8).map { ep(it) },
                            seasons = listOf(
                                BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season"),
                                BaseItemDto(Id = "s2", Name = "Season 2", Type = "Season"),
                            ),
                            selectedSeasonId = "s1",
                            trailer = TrailerSource.YouTube("dQw4w9WgXcQ", "Marshals trailer")),
                        repo, {}, { _, _ -> }, {}, {}, {},
                        showWatchlist = true,
                    )
                }
            }
        }
        rule.waitForIdle()

        // The header's old combined line is gone; the episode is named once, in the button.
        rule.onAllNodes(hasText("S1:E2 · Aired Mar 8, 2026")).assertCountEquals(0)
        rule.onAllNodes(hasText("Resume S1:E2")).assertCountEquals(1)
        rule.onAllNodes(hasText("Play from start")).assertCountEquals(1)
        // The air date is up top in the meta row...
        rule.onAllNodes(hasText("Aired Mar 8, 2026 · 52m")).assertCountEquals(1)
        // ...and the premiere date leads the genre line, no longer its own meta segment.
        rule.onAllNodes(hasText("Premiered Mar 1, 2026")).assertCountEquals(0)
        rule.onAllNodes(hasText("Premiered Mar 1, 2026  ·  Drama · Western")).assertCountEquals(1)
        // The standalone year still doesn't duplicate a shown date.
        rule.onAllNodes(hasText("2026")).assertCountEquals(0)

        val v = rule.activity.window.decorView
        val resting = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(resting))
        val f1 = File("build/outputs/qa/details-header-dedup.png"); f1.parentFile.mkdirs()
        f1.outputStream().use { resting.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CAPTURE ${f1.absolutePath}")

        // The scrolled page, as on the user's photo (focus below the header scrolls it up): how close
        // does the logo ink get to the screen's top edge?
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Episode 8", substring = true))
        rule.waitForIdle()
        val scrolled = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(scrolled))
        val f2 = File("build/outputs/qa/details-header-dedup-scrolled.png")
        f2.outputStream().use { scrolled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CAPTURE ${f2.absolutePath}")
    }
}
