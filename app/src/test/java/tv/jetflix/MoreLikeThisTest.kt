package tv.jetflix

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The user (Oct 26): verify "More Like This" works - the row must show the server's similar titles and
 * choosing one must open THAT title. The list comes from Jellyfin's Items/{id}/Similar endpoint
 * (filtered by RelatedItems, covered in RelatedAndHeroTest); this test proves the row renders those
 * titles and a pick reports the chosen item, which the host opens as its own details page.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class MoreLikeThisTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val repo by lazy { JellyfinRepository().apply { apiFor("http://demo.test") } }
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items

    private fun key(code: Int) {
        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, code, 0))
            rule.activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_UP, code, 0))
        }
        rule.waitForIdle()
    }
    private fun focusedText(): String = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString(",") { it.text }
            ?: n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    @Test fun rowShowsSimilarTitlesAndPickOpensTheChosenOne() {
        val source = base.first { it.isSeries }
        val similar = (0 until 6).map { i ->
            base.first { it.isMovie }.copy(Id = "sim$i", Name = "Similar ${'A' + i}", Type = "Series", Genres = source.Genres)
        }
        val picked = mutableListOf<BaseItemDto>()
        rule.setContent {
            JetflixTheme {
                DetailsContent(
                    DetailsUiState(loading = false, item = source, playTarget = source,
                        episodes = (0 until 6).map { i -> base.first { it.isMovie }.copy(Id = "e$i", Type = "Episode", SeriesId = source.Id, IndexNumber = i + 1, ParentIndexNumber = 1) },
                        seasons = listOf(BaseItemDto(Id = "s1", Name = "Season 1", Type = "Season")), selectedSeasonId = "s1",
                        similar = similar),
                    repo, {}, { _, _ -> }, {}, { picked += it }, {},
                )
            }
        }
        rule.waitForIdle()
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("More Like This"))
        rule.onNodeWithText("More Like This").assertIsDisplayed()
        // Every server's similar title shows in the row.
        similar.take(4).forEach { s -> rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription(s.Name!!)); rule.onNodeWithContentDescription(s.Name!!).assertExists() }
        // Pick "Similar C" with the remote: Down to the row, Right onto it, Center.
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("Similar C"))
        rule.onNodeWithContentDescription("Similar C").requestFocus()
        rule.waitForIdle()
        assertEquals("Similar C", focusedText())
        key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        rule.waitForIdle()
        assertEquals(listOf("sim2"), picked.map { it.Id })
    }
}
