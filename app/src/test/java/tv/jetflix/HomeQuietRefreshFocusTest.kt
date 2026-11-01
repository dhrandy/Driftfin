package tv.jetflix

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import tv.jetflix.app.data.UserItemDataDto
import tv.jetflix.app.ui.home.HomeContent
import tv.jetflix.app.ui.home.HomeRefresh
import tv.jetflix.app.ui.home.HomeSection
import tv.jetflix.app.ui.home.HomeUiState
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The real Home screen while the quiet refresh swaps Continue Watching under it: focus must stay on the card the user
 * is on, rows appearing or disappearing must not steal it, and when the focused card itself is removed focus must land
 * somewhere visible instead of nowhere. Same sections go through HomeRefresh.merge as in the app.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeQuietRefreshFocusTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun movie(id: String, name: String, resume: Boolean = false) =
        BaseItemDto(Id = id, Name = name, Type = "Movie", UserData = if (resume) UserItemDataDto(PlaybackPositionTicks = 100) else null)
    private fun cont(vararg n: Int) = n.map { movie("c$it", "Cont $it", resume = true) }
    private val movies = HomeSection("Recently Added Movies", (1..8).map { movie("m$it", "Mov $it") }, wide = false)
    private val favs = HomeSection("Favorites", (1..8).map { movie("f$it", "Fav $it") }, wide = false)
    private val hero = movie("h1", "Hero Film")

    private val state = mutableStateOf(HomeUiState())

    private fun show(sections: List<HomeSection>) {
        state.value = HomeUiState(loading = false, hero = hero, heroes = emptyList(), sections = sections)
        rule.setContent { JetflixTheme { HomeContent(state.value, JellyfinRepository().apply { apiFor("http://demo.test") }, "demo", {}, {}, {}, {}) } }
        rule.waitForIdle()
    }

    private fun key(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle(); rule.mainClock.advanceTimeBy(900); rule.waitForIdle() }
    private fun label(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "") +
            (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString { it } ?: "") + n.children.joinToString("") { label(it) }
    private fun focused(): List<SemanticsNode> = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
    private fun focusedLabel(): String = focused().joinToString("|") { label(it) }
    private fun focusedTop(): Float = focused().first().boundsInRoot.top

    private fun refreshTo(resume: List<BaseItemDto>) {
        rule.runOnUiThread { state.value = state.value.copy(sections = HomeRefresh.merge(state.value.sections, resume, emptyList())) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
    }

    private fun visible(n: SemanticsNode): Boolean {
        val w = rule.onRoot().fetchSemanticsNode().boundsInRoot; val b = n.boundsInRoot
        return b.right > w.left && b.left < w.right && b.bottom > w.top && b.top < w.bottom
    }

    @Test fun focusStaysOnTheSameCardWhenContinueWatchingItemsChange() {
        show(listOf(HomeSection("Continue Watching", cont(1, 2, 3), true), movies, favs))
        key(Key.DirectionDown); key(Key.DirectionDown); key(Key.DirectionDown) // banner, Continue Watching, Recently Added Movies
        key(Key.DirectionRight)
        val before = focusedLabel(); val top = focusedTop()
        assertTrue("focus is on a Movies card: $before", before.contains("Mov 2"))
        refreshTo(cont(9, 1, 2, 3))
        assertEquals("same card still focused", before, focusedLabel())
        assertEquals("card did not move on screen", top, focusedTop(), 1f)
    }

    @Test fun aNewContinueWatchingRowAppearingAboveDoesNotStealFocus() {
        show(listOf(movies, favs))
        key(Key.DirectionDown); key(Key.DirectionDown) // banner, Recently Added Movies
        key(Key.DirectionRight)
        val before = focusedLabel()
        assertTrue("focus is on a Movies card: $before", before.contains("Mov 2"))
        refreshTo(cont(1, 2))
        assertEquals("focus did not jump to the new row or the banner", before, focusedLabel())
        assertTrue("the focused card is still on screen", visible(focused().first()))
    }

    @Test fun theContinueWatchingRowDisappearingBelowTheUserLeavesFocusAlone() {
        show(listOf(HomeSection("Continue Watching", cont(1, 2), true), movies, favs))
        key(Key.DirectionDown); key(Key.DirectionDown); key(Key.DirectionDown)
        val before = focusedLabel()
        assertTrue("focus is on a Movies card: $before", before.contains("Mov 1"))
        refreshTo(emptyList())
        assertEquals(before, focusedLabel())
        assertTrue(visible(focused().first()))
    }

    @Test fun focusOnARemovedCardFallsBackToSomethingVisible() {
        show(listOf(HomeSection("Continue Watching", cont(1, 2, 3), true), movies, favs))
        key(Key.DirectionDown); key(Key.DirectionDown) // banner, Continue Watching first card
        assertTrue("focus is on Cont 1: ${focusedLabel()}", focusedLabel().contains("Cont 1"))
        refreshTo(cont(2, 3)) // the watched title leaves the row
        val now = focused()
        assertTrue("focus must not be lost when its card is removed", now.isNotEmpty())
        assertTrue("and it must be on screen: ${focusedLabel()}", visible(now.first()))
    }
}
