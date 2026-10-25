package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.ui.components.PerfCounters

class PerfCountersTest {
    @Test fun countsOnlyWhileRecording() {
        PerfCounters.inc("home")
        PerfCounters.begin()
        repeat(3) { PerfCounters.inc("home") }
        PerfCounters.inc("cards")
        val lines = PerfCounters.end()
        PerfCounters.inc("home")
        assertEquals("runs: cards 1 home 3".split(" ").sorted(), lines[0].split(" ").sorted())
        assertTrue(lines[1].startsWith("gc runs"))
        PerfCounters.begin()
        assertEquals("runs: none", PerfCounters.end()[0])
    }
}
