package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.PosterSize
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryContent
import tv.jetflix.app.ui.library.LibraryUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * Down 8 rows then back Up 8 rows at every poster size: the focused poster must settle fully below the
 * menu bar and its fade. Reported on the TV as the top row tucked under the bar after going down and back up.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LibraryReturnScrollTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("regression-items.json")!!.readText()).Items
    // Enough rows to scroll at the smallest size: 9 rows x 7 columns.
    private val items = (0 until 63).map { i -> base[i % base.size].copy(Id = "lib$i", Name = "Title $i") }

    @Test fun smallPostersClearTheBarComingBackUp() = checkSize(PosterSize.Small)

    @Test fun mediumPostersClearTheBarComingBackUp() = checkSize(PosterSize.Medium)

    @Test fun largePostersClearTheBarComingBackUp() = checkSize(PosterSize.Large)

    private fun checkSize(size: PosterSize) {
            AppSettings.setPosterSize(size)
            rule.setContent {
                JetflixTheme {
                    LibraryContent(LibraryUiState(loading = false, items = items),
                        JellyfinRepository().apply { apiFor("http://demo.test") }, JetflixTab.MOVIES, "demo", {}, {}, {})
                }
            }
            rule.waitForIdle()
            fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }
            fun cardTop(): Float = rule.onAllNodes(isFocused()).fetchSemanticsNodes().first().boundsInRoot.top
            // Bottom out on the last row (63 items: 9 rows at Small, 13 at Large), then come back up.
            repeat(12) { key(Key.DirectionDown) }
            val ups = mutableListOf<Float>()
            var top = Float.MAX_VALUE
            var steps = 0
            while (steps < 16) {
                key(Key.DirectionUp); top = cardTop(); ups += top; steps++
                if (top < 272f) break // row 0 settles at the rest position, below the fade line
            }
            println("LIBSCROLL_${size.name} UPS $ups")
            // While scrolled, the scroll spec keeps the focused row at 136 dp = 272 px (clear of bar + fade).
            val scrolled = ups.dropLast(1)
            assertTrue("${size.label}: scrolled settle under the fade line: $ups", scrolled.all { it >= 272f })
            // Back at the first row: rest position is 92 dp of top padding = 184 px at 2x, clear of the 76 dp
            // bar (152 px); the fade only draws once scrolled. The old 76 dp padding (152 px) sat flush under it.
            assertTrue("${size.label}: never settled back at the top row: $ups", top < 272f)
            assertTrue("${size.label}: top row tucked under the bar after returning, cardTop=$top (rest 184)", top >= 184f)
    }
}
