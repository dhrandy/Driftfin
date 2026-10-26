package tv.jetflix.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory counters that only count while the frame timing test is recording. They answer "how many times did Home,
 * the top bar or the image loader run during the 20 seconds", next to the frame numbers. Outside a test, [inc] does one
 * volatile read and returns. Nothing is stored, written or sent.
 */
object PerfCounters {
    @Volatile private var recording = false
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private var gcStart = LongArray(3)

    fun inc(name: String) {
        if (!recording) return
        counts.getOrPut(name) { AtomicInteger() }.incrementAndGet()
    }

    private fun gcNow(): LongArray = longArrayOf(stat("art.gc.gc-count"), stat("art.gc.blocking-gc-count"), stat("art.gc.blocking-gc-time"))
    private fun stat(key: String): Long = try { android.os.Debug.getRuntimeStat(key).toLong() } catch (_: Throwable) { 0L }

    /** Where a finished image request got its pixels: memory (already decoded), disk cache, or the network. */
    fun sourceName(src: coil.decode.DataSource): String = when (src) {
        coil.decode.DataSource.MEMORY_CACHE, coil.decode.DataSource.MEMORY -> "Mem"
        coil.decode.DataSource.DISK -> "Disk"
        coil.decode.DataSource.NETWORK -> "Net"
    }

    fun begin() { counts.clear(); gcStart = gcNow(); recording = true }

    /** Stops counting and returns two result lines. Block-time is the runtime's blocking GC total in ms. */
    fun end(): List<String> {
        recording = false
        val order = listOf("home", "navBar", "cards", "details")
        val parts = order.mapNotNull { k -> counts[k]?.let { "$k ${it.get()}" } }
        val imgOrder = listOf("imgLoads" to "started", "imgMem" to "memory", "imgDisk" to "disk", "imgNet" to "network", "imgCancel" to "cancelled", "imgError" to "failed")
        val imgParts = imgOrder.map { (k, label) -> "$label ${counts[k]?.get() ?: 0}" }
        val gc = gcNow()
        return listOf(
            "runs: " + (if (parts.isEmpty()) "none" else parts.joinToString(" ")),
            "images: " + imgParts.joinToString(" "),
            "gc runs ${gc[0] - gcStart[0]} | blocking gc ${gc[1] - gcStart[1]} (${gc[2] - gcStart[2]} ms)",
        )
    }
}

/** Counts each recomposition of the composable that calls it, while a frame test records. */
@Composable
inline fun CountRecompose(name: String) {
    SideEffect { PerfCounters.inc(name) }
}
