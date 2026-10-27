package tv.jetflix

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.NavSweep
import tv.jetflix.app.ui.components.TAB_OPEN_DELAY_MS
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.components.onLeftEdge
import tv.jetflix.app.ui.theme.JetflixTheme

/** The extra-Left-opens-Search rule and the top tabs that open their page as focus reaches them. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NavigationKeysTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun focusedText(): String = rule.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString(" || ") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString(",") { it.text } ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(",") ?: "?"
    }

    @Test fun leftAtTheLeftEdgeFiresOnceAndLeftElsewhereJustMoves() {
        var edge = 0
        val a = FocusRequester(); val b = FocusRequester()
        rule.setContent {
            Row(Modifier.onLeftEdge(enabled = true) { edge++ }) {
                Box(Modifier.size(40.dp).focusRequester(a).focusable().background(Color.Red))
                Box(Modifier.size(40.dp).focusRequester(b).focusable().background(Color.Blue))
            }
        }
        rule.runOnIdle { b.requestFocus() }
        press(Key.DirectionLeft)
        assertEquals("moved to the left box, no edge yet", 0, edge)
        rule.runOnIdle { assertTrue(rule.onAllNodes(isFocused()).fetchSemanticsNodes().size == 1) }
        press(Key.DirectionLeft)
        assertEquals("the fresh press after arriving at the edge fires (double click at the far left)", 1, edge)
    }

    @Test fun aSingleAccidentalLeftAtRestOnlyArms() {
        var edge = 0
        val a = FocusRequester()
        rule.setContent { Row(Modifier.onLeftEdge(enabled = true) { edge++ }) { Box(Modifier.size(40.dp).focusRequester(a).focusable()) } }
        rule.runOnIdle { a.requestFocus() }
        press(Key.DirectionLeft)
        assertEquals("one accidental Left at the edge must not open Search", 0, edge)
        press(Key.DirectionLeft)
        assertEquals("the deliberate second press fires", 1, edge)
    }

    @Test fun anyOtherKeyDisarmsTheEdgeChain() {
        var edge = 0
        val a = FocusRequester()
        rule.setContent { Row(Modifier.onLeftEdge(enabled = true) { edge++ }) { Box(Modifier.size(40.dp).focusRequester(a).focusable()) } }
        rule.runOnIdle { a.requestFocus() }
        press(Key.DirectionLeft) // arm
        assertEquals(0, edge)
        press(Key.DirectionDown) // navigate away: must disarm
        press(Key.DirectionLeft) // an unrelated later Left only arms again
        assertEquals("an unrelated later Left must not fire", 0, edge)
        press(Key.DirectionLeft)
        assertEquals("only the deliberate second press fires", 1, edge)
    }

    @Test fun holdingLeftStopsAtTheEdgeAndNeverFires() {
        var edge = 0
        val a = FocusRequester(); val b = FocusRequester()
        rule.setContent {
            Row(Modifier.onLeftEdge(enabled = true) { edge++ }) {
                Box(Modifier.size(40.dp).focusRequester(a).focusable())
                Box(Modifier.size(40.dp).focusRequester(b).focusable())
            }
        }
        rule.runOnIdle { b.requestFocus() }
        // A held key arrives as one KeyDown plus repeats (repeatCount > 0) without a KeyUp: it moves to
        // the edge, then stops there. (performKeyInput refuses repeat-downs, so inject the real events.)
        fun key(action: Int, repeat: Int) = android.view.KeyEvent(0, 0, action, android.view.KeyEvent.KEYCODE_DPAD_LEFT, repeat)
        rule.runOnUiThread {
            rule.activity.dispatchKeyEvent(key(android.view.KeyEvent.ACTION_DOWN, 0))
            rule.activity.dispatchKeyEvent(key(android.view.KeyEvent.ACTION_DOWN, 1))
            rule.activity.dispatchKeyEvent(key(android.view.KeyEvent.ACTION_DOWN, 2))
            rule.activity.dispatchKeyEvent(key(android.view.KeyEvent.ACTION_UP, 0))
        }
        rule.waitForIdle()
        assertEquals("holding Left to the boundary never opens Search", 0, edge)
        press(Key.DirectionLeft)
        assertEquals("a fresh press at the edge after release fires", 1, edge)
    }

    @Test fun leftIsIgnoredWhenDisabled() {
        var edge = 0
        val a = FocusRequester()
        rule.setContent { Row(Modifier.onLeftEdge(enabled = false) { edge++ }) { Box(Modifier.size(40.dp).focusRequester(a).focusable()) } }
        rule.runOnIdle { a.requestFocus() }
        press(Key.DirectionLeft)
        assertEquals(0, edge)
    }

    @Test fun leftBlockedByAFocusRule_stillCountsAsTheEdge() {
        var edge = 0
        val a = FocusRequester()
        rule.setContent {
            Row(Modifier.onLeftEdge(enabled = true) { edge++ }) {
                Box(Modifier.size(40.dp).focusRequester(a).focusProperties { left = FocusRequester.Cancel }.focusable())
            }
        }
        rule.runOnIdle { a.requestFocus() }
        press(Key.DirectionLeft)
        assertEquals("first edge press at rest arms", 0, edge)
        press(Key.DirectionLeft)
        assertEquals("second edge press fires", 1, edge)
    }

    @Test fun movingAcrossTheTabsOpensEachPageWithoutOkAndKeepsFocusOnTheTabs() {
        var selected by mutableStateOf(JetflixTab.HOME)
        val opened = mutableListOf<JetflixTab>()
        NavSweep.active = false
        rule.setContent {
            JetflixTheme {
                // Each page owns its own bar, as in the app, so the bar is rebuilt when the page changes.
                androidx.compose.runtime.key(selected) {
                    val navFocus = remember { FocusRequester() }
                    androidx.compose.runtime.LaunchedEffect(Unit) { if (!NavSweep.active) navFocus.requestFocus() }
                    TopNavBar(selected = selected, userName = "Demo", onTabSelected = { opened += it; selected = it }, onUserClick = {}, selectedFocus = navFocus, solid = true)
                }
            }
        }
        rule.waitForIdle()
        assertEquals("Home", focusedText())
        press(Key.DirectionRight)
        assertEquals("Shows", focusedText())
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals(listOf(JetflixTab.SHOWS), opened)
        assertEquals("page switched, focus still on the Shows tab", "Shows", focusedText())
        press(Key.DirectionRight)
        assertEquals("Movies", focusedText())
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals(listOf(JetflixTab.SHOWS, JetflixTab.MOVIES), opened)
        assertEquals("Movies", focusedText())
    }

    @Test fun aQuickSweepAcrossSeveralTabsOnlyOpensWhereFocusRests() {
        var selected by mutableStateOf(JetflixTab.HOME)
        val opened = mutableListOf<JetflixTab>()
        NavSweep.active = false
        rule.setContent {
            JetflixTheme {
                androidx.compose.runtime.key(selected) {
                    val navFocus = remember { FocusRequester() }
                    androidx.compose.runtime.LaunchedEffect(Unit) { if (!NavSweep.active) navFocus.requestFocus() }
                    TopNavBar(selected = selected, userName = "Demo", onTabSelected = { opened += it; selected = it }, onUserClick = {}, selectedFocus = navFocus, solid = true)
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false // a real quick sweep: no time passes between key presses
        press(Key.DirectionRight); press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals(listOf(JetflixTab.MOVIES), opened)
    }

    @Test fun enteringTheBarOpensTheFocusedTabAfterTheDwell() {
        // Straightforward semantics: a focused tab activates, with no first-entry exception. In the real app
        // entry lands on the already-selected tab, so this only fires when the user rests on a different tab.
        var opened = 0
        val other = FocusRequester()
        NavSweep.active = false
        rule.setContent {
            JetflixTheme {
                TopNavBar(selected = JetflixTab.HOME, userName = "Demo", onTabSelected = { opened++ }, onUserClick = {}, solid = true)
            }
        }
        rule.waitForIdle()
        press(Key.DirectionDown) // focus enters the bar on a tab that is not the selected one
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 300); rule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun aCompletedSweepClearsTheSweepFlagSoLaterScreensTakeFocus() {
        // Regression: a sweep that lands used to leave NavSweep.active set until the bar lost focus for
        // 120ms. A screen opened in that window (Search, Settings) skipped its own initial focus, and if
        // nothing moved focus off the hidden bar it stayed skipped. The sweep owns the flag and must
        // release it once the target tab holds focus.
        var selected by mutableStateOf(JetflixTab.HOME)
        NavSweep.active = false; NavSweep.tab = null
        rule.setContent {
            JetflixTheme {
                androidx.compose.runtime.key(selected) {
                    val navFocus = remember { FocusRequester() }
                    androidx.compose.runtime.LaunchedEffect(Unit) { if (!NavSweep.active) navFocus.requestFocus() }
                    TopNavBar(selected = selected, userName = "Demo", onTabSelected = { selected = it }, onUserClick = {}, selectedFocus = navFocus, solid = true)
                }
            }
        }
        rule.waitForIdle()
        assertEquals("Home", focusedText())
        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(TAB_OPEN_DELAY_MS + 100); rule.waitForIdle(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        assertEquals("Shows", focusedText())
        assertFalse("a completed sweep must clear NavSweep.active", NavSweep.active)
        assertEquals("a completed sweep must clear NavSweep.tab", null, NavSweep.tab)
    }
}
