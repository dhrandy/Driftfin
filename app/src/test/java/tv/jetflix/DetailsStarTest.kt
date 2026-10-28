package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.details.DetailsContent
import tv.jetflix.app.ui.details.DetailsUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The Watchlist star on Details: sits right after the heart, movies and shows only (Marquee's rule),
 * hidden when the Watchlist setting is off. OK on it toggles the list.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailsStarTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private fun repo() = JellyfinRepository().apply { apiFor("http://demo.test") }

    private fun setDetails(type: String = "Movie", watchlisted: Boolean = false, showWatchlist: Boolean = true, onToggle: () -> Unit = {}) {
        val item = base.first { it.Type == "Movie" }.copy(Type = type)
        rule.setContent {
            JetflixTheme {
                DetailsContent(
                    DetailsUiState(loading = false, item = item, playTarget = item), repo(), {}, { _, _ -> }, {}, {}, {},
                    watchlisted = watchlisted, showWatchlist = showWatchlist, onToggleWatchlist = onToggle,
                )
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    @Test fun starSitsBeforeTheHeartAndOkToggles() {
        var toggled = 0
        setDetails(onToggle = { toggled++ })
        val heart = rule.onNodeWithContentDescription("Add to favorites").assertExists()
        val star = rule.onNodeWithContentDescription("Add to watchlist").assertExists()
        assertTrue(
            "the star sits before (left of) the heart",
            star.fetchSemanticsNode().boundsInRoot.left < heart.fetchSemanticsNode().boundsInRoot.left,
        )
        star.requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, toggled)
    }

    @Test fun dpadWalksWatchedStarHeart() {
        setDetails()
        val watched = rule.onNodeWithContentDescription("Mark as watched").assertExists()
        watched.requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add to watchlist").assertExists()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add to favorites").assertExists()
    }

    @Test fun starredStarSaysSo() {
        setDetails(watchlisted = true)
        rule.onNodeWithContentDescription("In watchlist. Press OK to remove").assertExists()
    }

    @Test fun noStarOnEpisodes() {
        setDetails(type = "Episode")
        rule.onNodeWithContentDescription("Add to favorites").assertExists()
        rule.onNodeWithContentDescription("Add to watchlist").assertDoesNotExist()
    }

    @Test fun fullWatchlistSaysSo() {
        val item = base.first { it.Type == "Movie" }
        rule.setContent {
            JetflixTheme {
                DetailsContent(
                    DetailsUiState(loading = false, item = item, playTarget = item), repo(), {}, { _, _ -> }, {}, {}, {},
                    watchlisted = false, showWatchlist = true, watchlistFull = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Watchlist is full (500 titles). Remove one to add another.").assertExists()
    }

    @Test fun noStarWhenTheSettingIsOff() {
        setDetails(showWatchlist = false)
        rule.onNodeWithContentDescription("Add to favorites").assertExists()
        rule.onNodeWithContentDescription("Add to watchlist").assertDoesNotExist()
    }
}
