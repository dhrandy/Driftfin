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
