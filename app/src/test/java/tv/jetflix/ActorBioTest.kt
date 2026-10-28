package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDtoQueryResult
import tv.jetflix.app.ui.actor.ActorContent
import tv.jetflix.app.ui.actor.ActorUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The actor-page biography: Left from the filmography focuses the bio (with the "OK" hint),
 * OK opens the full scrollable view, Back closes it and lands back on the bio. The page bio
 * stays ellipsized; only the overlay shows the whole text, so an open overlay means two
 * semantics nodes carry the bio text and a closed one means one.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ActorBioTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val base = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString<BaseItemDtoQueryResult>(javaClass.classLoader!!.getResource("demo-items.json")!!.readText()).Items

    private val bio = buildString {
        append("Alex Example grew up in a small town. ")
        repeat(30) { append("They trained for years and worked across stage and screen, earning quiet praise for patient, precise performances. ") }
        append("FINALCHAPTER marker.")
    }

    private fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }

    private fun setActor() {
        val movies = base.take(10).mapIndexed { i, m -> m.copy(Name = "Movie ${i + 1}") }
        rule.setContent {
            JetflixTheme { ActorContent(ActorUiState(loading = false, name = "Alex Example", bio = bio, photoUrl = null, titles = movies), imageUrlFor = { null }, onOpenTitle = {}) }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
        key(Key.DirectionDown) // first key establishes focus; lands on the first movie card
    }

    private fun bioNodeCount(): Int = rule.onAllNodesWithText(bio).fetchSemanticsNodes().size

    @Test fun leftFromTheFilmographyFocusesTheBio() {
        setActor()
        rule.onNodeWithText("OK: read the full biography").assertDoesNotExist()
        key(Key.DirectionLeft)
        rule.onNodeWithText("OK: read the full biography").assertExists()
    }

    @Test fun okOpensTheFullBiographyAndBackRestoresTheBioFocus() {
        setActor()
        assertEquals(1, bioNodeCount())
        key(Key.DirectionLeft)
        key(Key.DirectionCenter)
        assertEquals("the overlay shows the whole bio alongside the ellipsized page copy", 2, bioNodeCount())
        key(Key.DirectionDown) // scrolls inside the overlay; it must stay open
        assertEquals(2, bioNodeCount())
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } // what the TV BACK key invokes
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle()
        assertEquals("Back closes the overlay", 1, bioNodeCount())
        rule.onNodeWithText("OK: read the full biography").assertExists() // and focus is back on the bio
    }
}
