package tv.jetflix

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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

/**
 * Remote paths on the Search page. Old behaviour (the Search tab's Down is hard-wired to the
 * text field) fails the idle tests; the queried and Up tests pin what must not regress.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SearchRecsFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var fm: androidx.compose.ui.focus.FocusManager? = null
    private val clicked = mutableListOf<String>()

    private fun fieldFocused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().any { it.config.contains(SemanticsProperties.EditableText) }
    private fun anyFocused() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
    private fun searchTab() = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search") and androidx.compose.ui.test.hasClickAction())[0]
    private fun searchTabFocused() = rule.onAllNodes(isFocused() and androidx.compose.ui.test.hasContentDescription("Search")).fetchSemanticsNodes().isNotEmpty()
    private fun posterFocused(title: String) = rule.onAllNodes(isFocused() and androidx.compose.ui.test.hasContentDescription(title)).fetchSemanticsNodes().isNotEmpty()
    private fun focusSearchTab() { searchTab().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus); rule.waitForIdle() }
    private fun move(d: FocusDirection) { rule.runOnUiThread { fm!!.moveFocus(d) }; rule.waitForIdle() }
    private fun ok() { rule.onAllNodes(isFocused())[0].performKeyInput { pressKey(Key.DirectionCenter) }; rule.waitForIdle() }

    private fun show(state: SearchUiState) {
        rule.setContent {
            JetflixTheme {
                fm = androidx.compose.ui.platform.LocalFocusManager.current
                SearchContent(state, JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, { clicked += it.Id }, {})
            }
        }
        rule.waitForIdle()
    }

    private fun recs() = SearchUiState(recommendations = listOf(
        "Recommended Movies" to listOf(BaseItemDto(Id = "m1", Name = "Rec One", Type = "Movie"), BaseItemDto(Id = "m2", Name = "Rec Two", Type = "Movie")),
        "Recommended Shows" to listOf(BaseItemDto(Id = "s1", Name = "Show One", Type = "Series")),
    ))

    @Test fun idleDownFromFieldReachesFirstRecommendationNotTheKeyboard() {
        show(recs())
        assertTrue("field starts focused", fieldFocused())
        move(FocusDirection.Down)
        assertFalse("Down leaves the field", fieldFocused())
        assertTrue("the first recommended poster (Rec One) holds focus", posterFocused("Rec One"))
        ok()
        assertEquals(listOf("m1"), clicked)
    }

    @Test fun idleDownFromMenuReachesFirstRecommendationNotTheField() {
        show(recs())
        focusSearchTab() // keyboard dismissed, focus on the Search tab (the observed stuck state)
        assertTrue("the Search tab holds focus", searchTabFocused())
        assertFalse(fieldFocused())
        move(FocusDirection.Down)
        assertFalse("Down from the Search tab does not reopen the keyboard field", fieldFocused())
        assertTrue("the first recommended poster (Rec One) holds focus", posterFocused("Rec One"))
        ok()
        assertEquals(listOf("m1"), clicked)
    }

    @Test fun idleUpFromRecommendationReturnsToFieldForTyping() {
        show(recs())
        move(FocusDirection.Down)
        assertTrue(posterFocused("Rec One"))
        move(FocusDirection.Up)
        assertTrue("Up from the posters refocuses the field", fieldFocused())
        rule.onNode(hasSetTextAction()).assertExists()
    }

    @Test fun queriedDownFromFieldReachesFirstResult() {
        show(SearchUiState(query = "big", results = listOf(BaseItemDto(Id = "r1", Name = "Big", Type = "Movie"), BaseItemDto(Id = "r2", Name = "Bigger", Type = "Movie"))))
        move(FocusDirection.Down)
        assertFalse(fieldFocused())
        assertTrue("first result poster (Big) holds focus", posterFocused("Big"))
        ok()
        assertEquals(listOf("r1"), clicked)
    }

    @Test fun queriedDownFromMenuStillGoesToTheField() {
        show(SearchUiState(query = "big", results = listOf(BaseItemDto(Id = "r1", Name = "Big", Type = "Movie"))))
        focusSearchTab()
        assertTrue(searchTabFocused())
        move(FocusDirection.Down)
        assertTrue("with a query the Search tab's Down target is unchanged", fieldFocused())
    }
}
