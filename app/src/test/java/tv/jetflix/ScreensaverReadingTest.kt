package tv.jetflix

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import tv.jetflix.app.ui.components.ScreensaverRules

class ScreensaverReadingTest {
    @Test fun scrollsAboutOneLinePerTwoAndAHalfSeconds() {
        // 10 lines of 21dp at xhdpi (density 2)
        val ms = ScreensaverRules.scrollMs(distancePx = (10 * 21 * 2), density = 2f)
        assertEquals(25_000.0, ms.toDouble(), 600.0)
    }
    @Test fun neverZero() = assertTrue(ScreensaverRules.scrollMs(0, 2f) >= 1)
    @Test fun longerTextTakesLonger() = assertTrue(ScreensaverRules.scrollMs(2000, 2f) > ScreensaverRules.scrollMs(500, 2f))

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun readsThroughThenHoldsAndOnlyThenReleasesTheNextSlide() = runTest {
        val log = mutableListOf<String>(); var reading = false
        val job = launch {
            ScreensaverRules.readThrough({ 840 }, 2f, { t, ms -> log += "scroll $t in $ms"; delay(ms.toLong()) }, { reading = it; log += "reading=$it" })
        }
        advanceTimeBy(2_400); runCurrent()
        assertTrue("waiting before scrolling", reading); assertEquals(listOf("reading=true"), log)
        advanceTimeBy(200); runCurrent()
        assertEquals("scroll 840 in 50000", log[1])
        advanceTimeBy(49_900); runCurrent()
        assertTrue("still holding at the end", reading)
        advanceTimeBy(3_100); runCurrent()
        assertFalse("released after scroll plus hold", reading); assertTrue(job.isCompleted)
    }
    @Test fun shortTextReleasesAfterOnlyTheStartPause() = runTest {
        var reading = false
        val job = launch { ScreensaverRules.readThrough({ 0 }, 2f, { _, _ -> fail("no scroll") }, { reading = it }) }
        advanceTimeBy(2_400); runCurrent(); assertTrue(reading)
        advanceTimeBy(200); runCurrent(); assertFalse(reading); assertTrue(job.isCompleted)
    }
}
