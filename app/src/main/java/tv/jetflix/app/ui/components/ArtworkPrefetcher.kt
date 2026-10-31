package tv.jetflix.app.ui.components

import android.content.Context
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import java.util.concurrent.atomic.AtomicInteger

/**
 * One artwork warm-up. [width]/[height] are the pixels the visible card will ask for; Coil's
 * memory cache only serves a request whose size matches the cached bitmap (within a pixel),
 * so warming without them heats the disk cache but not memory. 0 means "no explicit size",
 * which is fine for small server-resized art where a disk hit is cheap.
 *
 * [memoryCache] false keeps the decoded bitmap out of the memory cache (disk warm only), for
 * art too big to hold speculatively on a memory-tight device.
 */
data class ArtworkSpec(val url: String, val width: Int = 0, val height: Int = 0, val memoryCache: Boolean = true) {
    internal val key get() = if (width > 0 && height > 0) "$url@$width x$height" else url
}

/** The request a warm-up makes: same URL and size the visible card will use. */
fun artworkRequest(context: Context, spec: ArtworkSpec): ImageRequest =
    ImageRequest.Builder(context).data(spec.url)
        .apply {
            if (spec.width > 0 && spec.height > 0) size(spec.width, spec.height)
            if (!spec.memoryCache) memoryCachePolicy(CachePolicy.READ_ONLY)
        }
        .build()

/**
 * Warms Coil's caches for artwork that is about to become visible, so a focus move or the
 * banner rotation does not start a network fetch and decode while the screen is scrolling.
 *
 * Requests go through the app's normal image loader, so they are the same authenticated
 * reads the cards would make themselves a moment later. The bounds keep speculation cheap:
 * at most [maxPerBatch] new requests per call, each artwork requested at most once per
 * [recentLimit]-entry window, a new batch cancels still-running requests that fell out of
 * the new window, [cancelAll] runs when the owning screen leaves composition, and a shared
 * ceiling ([MAX_IN_FLIGHT]) stops row and hero warm-ups from stacking across screens.
 */
class ArtworkPrefetcher(
    private val requestFor: (ArtworkSpec) -> ImageRequest,
    private val enqueue: (ImageRequest) -> Disposable,
    private val maxPerBatch: Int = 4,
    private val recentLimit: Int = 96,
) {
    companion object {
        /** Total in-flight prefetches allowed across every screen's prefetcher; matches the image client's per-host cap so warm-ups are not the tighter bottleneck. */
        internal const val MAX_IN_FLIGHT = 12
        private val inFlight = AtomicInteger(0)

        /** In-flight prefetches right now, across all prefetchers; visible for tests. */
        internal fun inFlightCount(): Int = inFlight.get()
    }

    private val lock = Any()

    // Access-ordered so the least recently touched artwork drops out first once full.
    private val recent = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean = size > recentLimit
    }
    private val pending = HashMap<String, Disposable>()

    /** Requests artwork for [specs] in priority order; repeats, anything beyond [maxPerBatch], and anything over the shared in-flight ceiling are skipped. */
    fun prefetch(specs: List<ArtworkSpec>) {
        val wanted = specs.distinctBy { it.key }.take(maxPerBatch)
        synchronized(lock) {
            val wantedKeys = wanted.mapTo(HashSet()) { it.key }
            val toDispose = mutableListOf<Disposable>()
            val it = pending.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.key !in wantedKeys) {
                    it.remove()
                    toDispose += entry.value
                }
            }
            // Disposal fires the completion callback, which touches pending: only safe after iteration.
            toDispose.forEach { if (!it.isDisposed) it.dispose() }
            for (spec in wanted) {
                if (recent.containsKey(spec.key)) continue
                if (inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
                    inFlight.decrementAndGet()
                    break // ceiling reached; the next focus move retries whatever was skipped
                }
                recent[spec.key] = true
                val disposable = enqueue(requestFor(spec))
                pending[spec.key] = disposable
                disposable.job.invokeOnCompletion {
                    inFlight.decrementAndGet()
                    synchronized(lock) { pending.remove(spec.key) }
                }
            }
        }
    }

    /** Cancels everything still running. Call when the owning screen leaves composition. */
    fun cancelAll() {
        val toDispose: List<Disposable>
        synchronized(lock) {
            toDispose = pending.values.toList()
            pending.clear()
        }
        // Disposal fires the completion callback, which touches pending: only safe outside the map snapshot.
        toDispose.forEach { if (!it.isDisposed) it.dispose() }
    }

    /** Requests still running; visible for tests. */
    internal fun pendingCount(): Int = synchronized(lock) { pending.size }

    /** Distinct artworks already requested; visible for tests. */
    internal fun rememberedCount(): Int = synchronized(lock) { recent.size }
}
