package tv.jetflix.phone

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.MainTestClock
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic only. If a test fails with AppNotIdleException, prints which of Compose's three "keep pumping" conditions
 * is true (pending snapshot changes, recomposer pending work, or neither, which leaves a frame awaiter on the test clock),
 * how many recomposers are alive, and who writes state. Does not change any assertion, wait or timeout: the original
 * failure is rethrown unchanged. Place it INSIDE the compose rule (RuleChain.outerRule(compose).around(probe)) so the
 * composition is still alive when it reports.
 */
class NonIdleProbe(private val clock: () -> MainTestClock) : TestRule {
    private val writes = AtomicLong()
    private val applies = AtomicLong()
    private val writerStacks = ConcurrentHashMap<String, AtomicLong>()
    private val changedClasses = ConcurrentHashMap<String, AtomicLong>()
    @Volatile private var baselineRecomposers = -1

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            baselineRecomposers = runCatching { Recomposer.runningRecomposers.value.size }.getOrDefault(-1)
            val writeHandle = Snapshot.registerGlobalWriteObserver {
                val n = writes.incrementAndGet()
                if (n <= 30 || n % 5000L == 0L) {
                    val sig = Throwable().stackTrace
                        .filterNot { e -> e.className.startsWith("androidx.compose.runtime.snapshots") || e.className.contains("SnapshotMutableState") }
                        .take(12).joinToString("\n") { e -> "    at $e" }
                    writerStacks.getOrPut(sig) { AtomicLong() }.incrementAndGet()
                }
            }
            val applyHandle = Snapshot.registerApplyObserver { changed, _ ->
                applies.incrementAndGet()
                if (changedClasses.size < 10) changed.take(3).forEach { o -> changedClasses.getOrPut(o.javaClass.name) { AtomicLong() }.incrementAndGet() }
            }
            try {
                base.evaluate()
            } catch (t: Throwable) {
                if (isNonIdle(t)) runCatching { report(description, t) }
                throw t
            } finally {
                writeHandle.dispose()
                applyHandle.dispose()
            }
        }
    }

    private fun isNonIdle(t: Throwable): Boolean {
        val seen = HashSet<Throwable>()
        fun walk(x: Throwable?): Boolean {
            if (x == null || !seen.add(x)) return false
            return x.javaClass.simpleName == "AppNotIdleException" || walk(x.cause) || x.suppressed.any { s -> walk(s) }
        }
        return walk(t)
    }

    private fun sample(label: String): String {
        val pending = Snapshot.current.hasPendingChanges()
        val rs = Recomposer.runningRecomposers.value
        val info = rs.joinToString(",") { r -> (if (r.hasPendingWork) "W" else "-") + r.changeCount }
        return "$label snapshotPending=$pending recomposers=${rs.size}[$info] writes=${writes.get()} applies=${applies.get()}"
    }

    private fun report(description: Description, t: Throwable) {
        val sb = StringBuilder("NONIDLE-PROBE ${description.methodName} (${t.javaClass.simpleName})\n")
        val c = clock()
        val wasAutoAdvance = c.autoAdvance
        sb.append("clock autoAdvance=$wasAutoAdvance t=${c.currentTime}ms recomposersAtTestStart=$baselineRecomposers\n")
        sb.append(sample("at-timeout")).append('\n')
        // Step a few frames by hand (autoAdvance off) and re-read the flags, to see which stays true.
        try {
            c.autoAdvance = false
            repeat(5) { i ->
                c.advanceTimeByFrame()
                sb.append(sample("after-frame-${i + 1}")).append('\n')
            }
        } catch (e: Throwable) {
            sb.append("frame stepping failed: ${e.javaClass.simpleName}\n")
        } finally {
            c.autoAdvance = wasAutoAdvance
        }
        changedClasses.entries.take(10).forEach { sb.append("  changed ${it.key} x${it.value.get()}\n") }
        writerStacks.entries.sortedByDescending { it.value.get() }.take(5).forEach { sb.append("  sampled writer x${it.value.get()}:\n${it.key}\n") }
        println(sb.toString().take(6000))
    }
}
