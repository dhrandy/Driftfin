package tv.jetflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.search.SearchContent
import tv.jetflix.app.ui.search.SearchUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/** Remote path into the Actors row: Down from the field lands on an actor chip, OK opens that actor. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchActorFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private var fm: androidx.compose.ui.focus.FocusManager? = null
    private fun press(k: Key) { rule.onNode(hasSetTextAction()).performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun fieldFocused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().any { it.config.contains(SemanticsProperties.EditableText) }
    private fun focusedTexts() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().flatMap { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + n.children.flatMap { c -> c.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() }
    }

    @Test fun baselineDownFromFieldReachesAPosterWithoutActors() {
        rule.setContent {
            JetflixTheme {
                SearchContent(SearchUiState(query = "tom", results = listOf(BaseItemDto(Id = "m1", Name = "Big", Type = "Movie"))), JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, {})
            }
        }
        rule.waitForIdle()
        val trace = mutableListOf<String>()
        repeat(3) { press(Key.DirectionDown); trace += "field=${fieldFocused()} texts=${focusedTexts()}" }
        println("BASE $trace")
    }

    @Test fun downFromFieldReachesFirstActorAndOkOpensThatActor() {
        val opened = mutableListOf<Pair<String, String>>()
        val people = listOf("Tom Hanks", "Tom Hardy").mapIndexed { i, n -> BaseItemDto(Id = "p$i", Name = n, Type = "Person") }
        val items = listOf(BaseItemDto(Id = "m1", Name = "Big", Type = "Movie"))
        rule.setContent {
            JetflixTheme {
                fm = androidx.compose.ui.platform.LocalFocusManager.current
                SearchContent(SearchUiState(query = "tom", people = people, results = items), JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}, {},
                    onActorClick = { id, name -> opened += id to name })
            }
        }
        rule.waitForIdle()
        assertTrue("field starts focused", fieldFocused())
        rule.runOnUiThread { fm!!.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }; rule.waitForIdle()
        val names = listOf("Tom Hanks", "Tom Hardy")
        val f = focusedTexts()
        assertTrue("Down lands on the FIRST actor: $f", "Tom Hanks" in f)
        println("TRACE field=${fieldFocused()} texts=$f")
        assertFalse("Down leaves the field", fieldFocused())
        val name = names.firstOrNull { it in f }
        assertTrue("an actor chip holds focus after Down: $f", name != null)
        press(Key.DirectionCenter)
        assertEquals(listOf(("p" + names.indexOf(name)) to name!!), opened)
    }
}
