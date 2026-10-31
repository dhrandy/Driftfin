package tv.jetflix

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.LocalTestArtwork
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** A series page shows the server's series status on the quiet genre line ("Premiered ... · Continuing ·
 *  Drama · Crime"), for one-season and genre-less series too - not on the season controls row. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SeriesStatusCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json").readText()).Items
    // A coherent modern series: the base fixture's ids and images, contemporary dates and summary.
    private val series = base.first { it.isSeries }.copy(
        Name = "Marshals", ProductionYear = 2023, PremiereDate = "2023-03-01T00:00:00.0000000Z",
        Overview = "A deputy U.S. marshal balances family and fugitives in the modern West.",
        Genres = listOf("Drama", "Crime"),
    )
    private val ep = base.first { it.isMovie }.copy(
        Id = "ep1", Type = "Episode", SeriesName = series.Name, SeriesId = series.Id,
        ParentIndexNumber = 1, IndexNumber = 1, Name = "Pilot", RunTimeTicks = 42L * 600_000_000,
        PremiereDate = "2023-03-01T00:00:00.0000000Z", Overview = "A fugitive's trail forces a reckoning.",
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

    private lateinit var ls: LazyListState

    private fun show(status: String?, genres: List<String> = listOf("Drama", "Crime"),
                     seasons: List<BaseItemDto> = listOf(
                         BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season"),
                         BaseItemDto(Id = "s2", Name = "Season 2", Type = "Season"),
                     )) {
        val repo = JellyfinRepository().apply { apiFor("http://demo.test") }
        rule.setContent {
            ls = rememberLazyListState()
            CompositionLocalProvider(LocalTestArtwork provides { _ -> null }) {
                JetflixTheme {
                    DetailsContent(
                        DetailsUiState(
                            loading = false,
                            item = series.copy(SeriesStatus = status, Genres = genres),
                            playTarget = ep,
                            episodes = listOf(ep),
                            seasons = seasons,
                            selectedSeasonId = seasons.firstOrNull()?.Id,
                        ),
                        repo, {}, { _, _ -> }, {}, {}, {}, listState = ls,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** Scrolls until the genre line is composed and on screen. */
    private fun scrollToGenreLine() {
        for (i in 0..10) {
            rule.runOnUiThread { runBlocking { ls.scrollToItem(i, 0) } }
            rule.waitForIdle()
            val info = ls.layoutInfo
            if (info.visibleItemsInfo.firstOrNull { it.key == "genres" }?.let { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset } == true) break
        }
    }

    @Test fun continuingShowsOnTheGenreLine() {
        show("Continuing")
        scrollToGenreLine()
        rule.onNodeWithText("Continuing", substring = true).assertIsDisplayed()
        capture("series-status-continuing")
    }

    @Test fun oneSeasonNoGenresStillShowsStatus() {
        show("Ended", genres = emptyList(), seasons = listOf(BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season")))
        scrollToGenreLine()
        rule.onNodeWithText("Ended", substring = true).assertIsDisplayed()
        capture("series-status-ended-single-season")
    }

    @Test fun unknownStatusShowsNothing() {
        show(null)
        rule.onNodeWithText("Continuing", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Ended", substring = true).assertDoesNotExist()
    }
}
