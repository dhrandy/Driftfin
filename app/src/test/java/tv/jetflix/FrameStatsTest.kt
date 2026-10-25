package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.components.FrameStatsMath

class FrameStatsTest {
    private fun ms(vararg v: Double) = v.map { (it * 1_000_000).toLong() }

    @Test fun emptyHasNoFrames() = assertEquals(0, FrameStatsMath.summarize(emptyList(), 60.0).frames)

    @Test fun budgetFollowsTheRefreshRate() {
        assertEquals(16.67, FrameStatsMath.summarize(ms(10.0), 60.0).budgetMs, 0.01)
        assertEquals(8.33, FrameStatsMath.summarize(ms(10.0), 120.0).budgetMs, 0.01)
        assertEquals(20.0, FrameStatsMath.summarize(ms(10.0), 50.0).budgetMs, 0.01)
    }

    @Test fun sameFramesCountDifferentlyAt60And120Hz() {
        val frames = ms(10.0, 10.0, 10.0, 20.0)
        assertEquals(25.0, FrameStatsMath.summarize(frames, 60.0).overBudgetPct, 0.01)
        assertEquals(100.0, FrameStatsMath.summarize(frames, 120.0).overBudgetPct, 0.01)
    }

    @Test fun percentilesAndWorst() {
        val s = FrameStatsMath.summarize(ms(*DoubleArray(100) { it + 1.0 }), 60.0)
        assertEquals(50.5, s.avgMs, 0.01)
        assertEquals(95.0, s.p95Ms, 0.01)
        assertEquals(100.0, s.worstMs, 0.01)
        assertEquals(67, s.overDoubleBudget) // 34 ms to 100 ms
    }
}

class FrameStageMathTest {
    // total, delay, input, anim, layout, draw, sync, issue, swap, gpu (ms)
    private fun row(vararg ms: Double) = LongArray(ms.size) { (ms[it] * 1_000_000).toLong() }

    @Test fun averagesAllFramesAndOnlySlowOnes() {
        val rows = listOf(
            row(10.0, 0.0, 0.0, 1.0, 2.0, 3.0, 1.0, 1.0, 1.0, 2.0),
            row(50.0, 0.0, 0.0, 5.0, 20.0, 10.0, 4.0, 5.0, 5.0, 30.0),
        )
        val all = tv.jetflix.app.ui.components.FrameStageMath.averages(rows)
        assertEquals(3.0, all[2], 0.001) // anim: (1 + 5) / 2
        assertEquals(11.0, all[3], 0.001) // layout: (2 + 20) / 2
        val slow = tv.jetflix.app.ui.components.FrameStageMath.averages(rows, 16.7)
        assertEquals(20.0, slow[3], 0.001) // only the 50 ms frame
        assertEquals(30.0, slow[8], 0.001) // gpu
    }

    @Test fun noFramesGivesZeros() {
        assertEquals(0.0, tv.jetflix.app.ui.components.FrameStageMath.averages(emptyList()).sum(), 0.0)
    }

    @Test fun lineOmitsGpuBeforeApi31() {
        val l = tv.jetflix.app.ui.components.FrameStageMath.line("x", List(9) { 1.0 }, withGpu = false)
        assertEquals(false, l.contains("gpu"))
        assertEquals(true, tv.jetflix.app.ui.components.FrameStageMath.line("x", List(9) { 1.0 }, withGpu = true).contains("gpu 1.0"))
    }
}
