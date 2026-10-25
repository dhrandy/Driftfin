package tv.jetflix.app.ui.components

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** Summary of recent frame durations, in milliseconds, against the display's frame budget. */
data class FrameSummary(
    val frames: Int, val hz: Double, val budgetMs: Double,
    val avgMs: Double, val p95Ms: Double, val worstMs: Double,
    val overBudgetPct: Double, val overDoubleBudget: Int,
) {
    fun lines(): List<String> =
        if (frames == 0) listOf("Frame timing: waiting for frames") else listOf(
            "%.0f Hz, budget %.1f ms | last %d frames".format(hz, budgetMs, frames),
            "avg %.1f | p95 %.1f | worst %.0f ms".format(avgMs, p95Ms, worstMs),
            "over budget %.0f%% | over 2x budget: %d".format(overBudgetPct, overDoubleBudget),
        )
}

object FrameStatsMath {
    /** A frame is "over budget" when it took longer than one display refresh; "2x" means at least two refreshes were missed. */
    fun summarize(durationsNs: List<Long>, hz: Double): FrameSummary {
        val budget = 1000.0 / hz.coerceAtLeast(1.0)
        if (durationsNs.isEmpty()) return FrameSummary(0, hz, budget, 0.0, 0.0, 0.0, 0.0, 0)
        val ms = durationsNs.map { it / 1_000_000.0 }.sorted()
        return FrameSummary(
            frames = ms.size, hz = hz, budgetMs = budget,
            avgMs = ms.average(),
            p95Ms = ms[((ms.size - 1) * 0.95).toInt()],
            worstMs = ms.last(),
            overBudgetPct = ms.count { it > budget } * 100.0 / ms.size,
            overDoubleBudget = ms.count { it > budget * 2 },
        )
    }
}

/**
 * Where frame time goes, from the platform's own per-stage numbers. Stages in order: unknown delay, input, animation,
 * layout/measure, draw (these run on the UI thread), then sync, command issue, swap buffers (render thread) and GPU (API 31+).
 * Averages are shown for all frames and for frames over budget, so a slow frame's main cost stands out.
 */
object FrameStageMath {
    val names = listOf("delay", "input", "anim", "layout", "draw", "sync", "issue", "swap", "gpu")

    /** Each row is [total, then the nine stages in the order of [names]], all nanoseconds. */
    fun averages(rows: List<LongArray>, onlyOverMs: Double? = null): List<Double> {
        val sel = if (onlyOverMs == null) rows else rows.filter { it[0] / 1_000_000.0 > onlyOverMs }
        if (sel.isEmpty()) return List(names.size) { 0.0 }
        return names.indices.map { i -> sel.sumOf { it[i + 1] } / sel.size / 1_000_000.0 }
    }

    fun line(label: String, avgs: List<Double>, withGpu: Boolean): String =
        label + " " + names.indices.filter { withGpu || names[it] != "gpu" }
            .joinToString(" ") { "${names[it]} %.1f".format(avgs[it]) }
}

sealed interface FrameTest {
    object Idle : FrameTest
    data class Recording(val secondsLeft: Int) : FrameTest
    data class Result(val lines: List<String>) : FrameTest
}

/**
 * A timed frame-timing test. Start it in Settings, move around the screens you want to measure for the whole window,
 * then read the result card. It uses the platform's FrameMetrics (the total time each frame took, as the app reports it),
 * keeps the numbers in memory only, and stops listening when the window ends. Nothing is saved or sent anywhere.
 */
object FrameStats {
    const val SECONDS = 20
    private const val MAX_FRAMES = 20_000
    private val frames = ArrayList<Long>()
    private val stages = ArrayList<LongArray>()
    private var thread: HandlerThread? = null
    private var listener: android.view.Window.OnFrameMetricsAvailableListener? = null
    private var window: android.view.Window? = null
    private val main = Handler(android.os.Looper.getMainLooper())

    private val _state = kotlinx.coroutines.flow.MutableStateFlow<FrameTest>(FrameTest.Idle)
    val state: kotlinx.coroutines.flow.StateFlow<FrameTest> = _state

    @Synchronized private fun add(m: FrameMetrics) {
        if (frames.size >= MAX_FRAMES) return
        val gpu = if (android.os.Build.VERSION.SDK_INT >= 31) m.getMetric(FrameMetrics.GPU_DURATION) else 0L
        frames.add(m.getMetric(FrameMetrics.TOTAL_DURATION))
        stages.add(longArrayOf(
            m.getMetric(FrameMetrics.TOTAL_DURATION),
            m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION), m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
            m.getMetric(FrameMetrics.ANIMATION_DURATION), m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
            m.getMetric(FrameMetrics.DRAW_DURATION), m.getMetric(FrameMetrics.SYNC_DURATION),
            m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION), m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION), gpu,
        ))
    }
    @Synchronized private fun copy(): List<Long> = ArrayList(frames)
    @Synchronized private fun copyStages(): List<LongArray> = ArrayList(stages)

    /** The display's current refresh rate, so the budget is right on 30, 50, 60 or 120 Hz screens. */
    private fun refreshHz(activity: Activity): Double {
        @Suppress("DEPRECATION")
        val r = activity.windowManager.defaultDisplay.refreshRate
        return if (r > 1f) r.toDouble() else 60.0
    }

    fun start(activity: Activity) {
        if (_state.value is FrameTest.Recording) return
        stopListening()
        synchronized(this) { frames.clear(); stages.clear() }
        val t = HandlerThread("frame-stats").also { it.start() }
        val l = android.view.Window.OnFrameMetricsAvailableListener { _, m, _ -> add(m) }
        activity.window.addOnFrameMetricsAvailableListener(l, Handler(t.looper))
        thread = t; listener = l; window = activity.window
        _state.value = FrameTest.Recording(SECONDS)
        tick(activity, SECONDS)
    }

    private fun tick(activity: Activity, left: Int) {
        main.postDelayed({
            if (_state.value !is FrameTest.Recording) return@postDelayed
            if (left <= 1) {
                stopListening()
                val summary = FrameStatsMath.summarize(copy(), refreshHz(activity))
                val rows = copyStages()
                val gpu = android.os.Build.VERSION.SDK_INT >= 31
                val stageLines = if (rows.isEmpty()) emptyList() else listOf(
                    FrameStageMath.line("all frames ms:", FrameStageMath.averages(rows), gpu),
                    FrameStageMath.line("slow frames ms:", FrameStageMath.averages(rows, summary.budgetMs), gpu),
                    "blur " + (if (tv.jetflix.app.data.AppSettings.liveBlur.value) "on" else "off"),
                )
                _state.value = FrameTest.Result(listOf("Frame timing, last $SECONDS seconds") + summary.lines() + stageLines)
            } else {
                _state.value = FrameTest.Recording(left - 1)
                tick(activity, left - 1)
            }
        }, 1000)
    }

    private fun stopListening() {
        listener?.let { window?.removeOnFrameMetricsAvailableListener(it) }
        thread?.quitSafely(); thread = null; listener = null; window = null
    }

    fun dismiss() { if (_state.value is FrameTest.Result) _state.value = FrameTest.Idle }

    /** Called when the Activity goes away: stop listening and drop any recording. */
    fun cancel() { main.removeCallbacksAndMessages(null); stopListening(); _state.value = FrameTest.Idle }
}

/** Tiny countdown while recording; the result card shows only after the window ends, so nothing covers the screen being measured. */
@Composable
fun FrameTestHost(modifier: Modifier = Modifier) {
    val st by FrameStats.state.collectAsState()
    when (val s = st) {
        FrameTest.Idle -> Unit
        is FrameTest.Recording -> Box(modifier.padding(6.dp).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text("REC ${s.secondsLeft}s", color = Color.White, fontSize = 10.sp)
        }
        is FrameTest.Result -> {
            androidx.activity.compose.BackHandler { FrameStats.dismiss() }
            FrameResultCard(s.lines)
        }
    }
}

@Composable
fun FrameResultCard(lines: List<String>) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(Modifier.background(Color(0xF0101010)).padding(28.dp)) {
            lines.forEachIndexed { i, l -> Text(l, color = Color.White, fontSize = if (i == 0) 20.sp else 18.sp) }
            Text("App-side frame time, not display latency. Press Back to close.", color = Color(0xFFAAAAAA), fontSize = 13.sp)
        }
    }
}
