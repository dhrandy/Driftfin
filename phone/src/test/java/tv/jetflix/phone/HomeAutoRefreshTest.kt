package tv.jetflix.phone

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.ui.HomeAutoRefresh

/** Cadence and lifecycle of the Home refresh loop, on the test clock (no real waiting). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAutoRefreshTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var calls = 0
    private val onHome = mutableStateOf(true)

    private val enabled = mutableStateOf(true)

    private fun start(interval: Long = 60_000L) {
        rule.mainClock.autoAdvance = false
        rule.setContent { if (onHome.value) HomeAutoRefresh(enabled = enabled.value, intervalMs = interval) { calls++ } }
        rule.mainClock.advanceTimeBy(1)
    }

    @Test fun firstStartDoesNotRefreshThenEveryIntervalWhileOnHome() {
        start()
        assertEquals("Home just did its full load", 0, calls)
        rule.mainClock.advanceTimeBy(59_000); assertEquals(0, calls)
        rule.mainClock.advanceTimeBy(1_500); assertEquals(1, calls)
        rule.mainClock.advanceTimeBy(60_000); assertEquals(2, calls)
    }

    @Test fun leavingHomeStopsTheLoop() {
        start()
        rule.mainClock.advanceTimeBy(61_000); assertEquals(1, calls)
        onHome.value = false
        rule.mainClock.advanceTimeBy(1); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300_000)
        assertEquals("no polling off Home", 1, calls)
    }

    @Test fun backgroundingStopsItAndReturnRefreshesOnceImmediately() {
        start()
        rule.mainClock.advanceTimeBy(61_000); assertEquals(1, calls)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.mainClock.advanceTimeBy(1)
        rule.mainClock.advanceTimeBy(300_000)
        assertEquals("no polling while the app is in the background", 1, calls)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(1)
        assertEquals("one refresh on return", 2, calls)
        rule.mainClock.advanceTimeBy(60_500)
        assertEquals("then the normal cadence resumes", 3, calls)
    }

    @Test fun pausedButStartedActivityDoesNotPoll() {
        // PAUSED = still visible but not in front (system dialog or another window over the app): STARTED, not RESUMED.
        start()
        rule.mainClock.advanceTimeBy(61_000); assertEquals(1, calls)
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.mainClock.advanceTimeBy(1)
        rule.mainClock.advanceTimeBy(300_000)
        assertEquals("no polling while the activity is paused", 1, calls)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.mainClock.advanceTimeBy(1)
        assertEquals("one refresh when it is in front again", 2, calls)
    }

    @Test fun settingOffNeverPolls() {
        enabled.value = false
        start()
        rule.mainClock.advanceTimeBy(300_000)
        assertEquals(0, calls)
    }
}
