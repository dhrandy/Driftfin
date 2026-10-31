package tv.jetflix

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import tv.jetflix.app.update.StartupUpdateNotice
import tv.jetflix.app.update.UpdateNoticeState
import tv.jetflix.app.update.UpdateSource
import tv.jetflix.app.update.Updater
import tv.jetflix.app.ui.theme.JetflixTheme

/**
 * The periodic update check, end to end through the real composable and a real Updater on a local server:
 * startup always checks, a new build pills once, the same build never re-pills, a failed check keeps the
 * current state, UpToDate clears it, and turning notifications off hides the pill and stops the rechecks
 * while the startup check still runs.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class UpdateCadenceTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val paths = CopyOnWriteArrayList<String>()
    private lateinit var server: MockWebServer
    @Volatile private var tag = "build-47"
    @Volatile private var latestCode = 200

    @After fun down() { server.shutdown(); UpdateNoticeState.available.value = null }

    private fun start() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path.orEmpty()
                    if (request.path == "/latest") {
                        if (latestCode != 200) return MockResponse().setResponseCode(latestCode)
                        val base = server.url("/dl/$tag/").toString()
                        return MockResponse().setBody(
                            """{"tag_name":"$tag","assets":[{"name":"jetflix-release.apk","browser_download_url":"${base}jetflix-release.apk","size":10}]}"""
                        )
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
            start()
        }
    }

    private fun updater() = Updater(UpdateSource(latestReleaseUrl = server.url("/latest").toString(),
        assetPrefix = server.url("/dl/").toString(), allowedHosts = setOf(server.hostName), requireHttps = false))

    private fun checks() = paths.count { it == "/latest" }
    private fun pillCount() = rule.onAllNodes(hasText("Update available", substring = true)).fetchSemanticsNodes().size
    private fun advanceUntilCheck(n: Int) {
        var t = 0L
        while (checks() < n && t < 30000) {
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            t += 500
        }
    }
    private fun until(ms: Long = 8000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (cond()) return
            Thread.sleep(25)
        }
    }

    @Test fun newBuildPillsOnceSameBuildQuietFailureKeepsUpToDateClears() {
        start()
        rule.mainClock.autoAdvance = false
        rule.setContent { JetflixTheme { StartupUpdateNotice(46, updater = { updater() }, recheckIntervalMs = 1_000) } }
        // Startup check always runs: pill + shared state. The badge fades IN over 1200ms of clock time.
        // Real-time poll first: the effect's start and the IO check need the main looper, not the clock.
        until { checks() >= 1 }
        assertEquals("startup check fires", true, checks() >= 1)
        until(2000) { UpdateNoticeState.available.value != null }
        until(3000) { rule.mainClock.advanceTimeByFrame(); pillCount() > 0 } // badge fade-in needs frames
        if (pillCount() == 0) rule.onRoot().printToLog("CADENCE")
        assertEquals("startup check pills on a new build", 1, pillCount())
        assertEquals(47, UpdateNoticeState.available.value?.build)
        // Pill fades; the 6h (1s here) recheck finds the SAME build: no second pill.
        advanceUntilCheck(2)
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        assertEquals("same build must not pill again", 0, pillCount())
        assertEquals(47, UpdateNoticeState.available.value?.build)
        // A failed check keeps the current state.
        latestCode = 500
        advanceUntilCheck(3)
        Thread.sleep(150); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals("a failed check keeps the available state", 47, UpdateNoticeState.available.value?.build)
        // UpToDate clears it.
        latestCode = 200; tag = "build-46"
        advanceUntilCheck(4)
        until(2000) { UpdateNoticeState.available.value == null }
        assertNull("UpToDate clears the available state", UpdateNoticeState.available.value)
        assertEquals("never re-pilled", 0, pillCount())
    }

    @Test fun noticesOffHidesPillAndStopsRechecksButStartupStillChecks() {
        start()
        rule.mainClock.autoAdvance = false
        val notices = MutableStateFlow(false)
        rule.setContent { JetflixTheme { StartupUpdateNotice(46, updater = { updater() }, recheckIntervalMs = 1_000, notices = notices) } }
        advanceUntilCheck(1)
        until(2000) { UpdateNoticeState.available.value != null }
        assertEquals("startup check still feeds Settings when notices are off", 47, UpdateNoticeState.available.value?.build)
        rule.mainClock.advanceTimeBy(9_500); rule.waitForIdle()
        assertEquals("no pill when notices are off", 0, pillCount())
        rule.mainClock.advanceTimeBy(5_000); rule.waitForIdle()
        Thread.sleep(200)
        assertEquals("no periodic rechecks while notices are off", 1, checks())
    }

    @Test fun rechecksPauseWhenBackgroundedAndReturnChecksImmediately() {
        start()
        rule.mainClock.autoAdvance = false
        rule.setContent { JetflixTheme { StartupUpdateNotice(46, updater = { updater() }, recheckIntervalMs = 1_000) } }
        advanceUntilCheck(1)
        // Composition survives ON_STOP; the lifecycle gate must stop the rechecks anyway.
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.mainClock.advanceTimeBy(5_000); rule.waitForIdle()
        Thread.sleep(200)
        assertEquals("no update checks while the app is backgrounded", 1, checks())
        // Back to the foreground: re-checks immediately (no waiting out the interval).
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        advanceUntilCheck(2)
        until(2000) { checks() >= 2 }
        assertEquals("returning to the foreground re-checks", true, checks() >= 2)
    }
}
