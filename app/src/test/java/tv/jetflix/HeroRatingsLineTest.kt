package tv.jetflix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.home.HeroBanner
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The Home banner's metadata line: age rating and community score below the title, above the
 * description. Hidden when the Settings toggle is off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HeroRatingsLineTest {
    @get:Rule val rule = createComposeRule()

    private val hero = BaseItemDto(
        Id = "h1", Name = "The Example Movie", Type = "Movie",
        Overview = "An overview long enough to sit under the metadata line.",
        OfficialRating = "PG-13", CommunityRating = 8.3f,
    )

    private fun banner(showRatings: Boolean) {
        rule.setContent {
            JetflixTheme { HeroBanner(hero = hero, backdropUrl = null, logoUrl = null, onPlay = {}, onMoreInfo = {}, showRatings = showRatings) }
        }
        rule.waitForIdle()
    }

    @Test fun ratingsLineShowsUnderTheTitle() {
        banner(true)
        rule.onNodeWithText("PG-13").assertIsDisplayed()
        rule.onNodeWithText("8.3").assertIsDisplayed()
        // Placement: below the title, above the overview.
        val title = rule.onNodeWithText("The Example Movie").fetchSemanticsNode().boundsInRoot
        val rating = rule.onNodeWithText("PG-13").fetchSemanticsNode().boundsInRoot
        val overview = rule.onNodeWithText("An overview long enough to sit under the metadata line.").fetchSemanticsNode().boundsInRoot
        assert(rating.top >= title.bottom - 1f) { "rating line should start at or below the title's bottom" }
        assert(rating.bottom <= overview.top + 1f) { "rating line should end above the overview" }
    }

    @Test fun hiddenWhenTheToggleIsOff() {
        banner(false)
        rule.onNodeWithText("PG-13").assertDoesNotExist()
        rule.onNodeWithText("8.3").assertDoesNotExist()
    }

    @Test fun noLineWhenTheItemHasNoRatings() {
        val bare = BaseItemDto(Id = "h2", Name = "Plain Movie", Type = "Movie", Overview = "No ratings at all.")
        rule.setContent {
            JetflixTheme { HeroBanner(hero = bare, backdropUrl = null, logoUrl = null, onPlay = {}, onMoreInfo = {}, showRatings = true) }
        }
        rule.waitForIdle()
        rule.onNodeWithText("No ratings at all.").assertIsDisplayed()
    }
}
