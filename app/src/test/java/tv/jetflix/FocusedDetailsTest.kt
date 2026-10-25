package tv.jetflix

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.home.FocusedDetails
import tv.jetflix.app.ui.theme.JetflixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusedDetailsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun showsTitleYearRatingRuntimeGenresAndSynopsis() {
        val item = BaseItemDto(
            Id = "1", Name = "Test Title", Type = "Movie", ProductionYear = 2019, OfficialRating = "PG-13",
            RunTimeTicks = 95L * 600_000_000, Genres = listOf("Comedy", "Drama"), Overview = "A short synopsis of the film.",
        )
        rule.setContent { JetflixTheme { FocusedDetails(item) } }
        rule.onNodeWithText("Test Title").assertIsDisplayed()
        rule.onNodeWithText("2019  |  PG-13  |  1h 35m  |  Comedy • Drama").assertIsDisplayed()
        rule.onNodeWithText("A short synopsis of the film.").assertIsDisplayed()
    }
}
