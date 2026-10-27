package tv.jetflix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.request.Disposable
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.CachePolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.ArtworkPrefetcher
import tv.jetflix.app.ui.components.ArtworkSpec
import tv.jetflix.app.ui.components.artworkRequest

/** Policy for the artwork warmer: what it asks for, what it skips and what it cancels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArtworkPrefetcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FakeDisposable : Disposable {
        val deferred = CompletableDeferred<ImageResult>()
        override val job: Deferred<ImageResult> get() = deferred
        var disposed = false
        override val isDisposed: Boolean get() = disposed
        override fun dispose() { disposed = true; deferred.cancel() }
    }

    private val created = mutableListOf<ArtworkPrefetcher>()

    @After fun tearDown() {
        // Prefetches are shared-ceiling state; leave none running between tests.
        created.forEach { it.cancelAll() }
        created.clear()
        val deadline = System.currentTimeMillis() + 2_000
        while (ArtworkPrefetcher.inFlightCount() != 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(0, ArtworkPrefetcher.inFlightCount())
    }

    private class Recorder {
        val enqueued = mutableListOf<String>()
        val disposables = mutableListOf<FakeDisposable>()
    }

    private fun prefetcher(rec: Recorder, maxPerBatch: Int = 4, recentLimit: Int = 96) = ArtworkPrefetcher(
        requestFor = { spec -> ImageRequest.Builder(context).data(spec.url).build() },
        enqueue = { req ->
            rec.enqueued.add(req.data.toString())
            FakeDisposable().also { rec.disposables.add(it) }
        },
        maxPerBatch = maxPerBatch,
        recentLimit = recentLimit,
    ).also { created.add(it) }

    private fun specs(vararg urls: String) = urls.map { ArtworkSpec(it) }

    @Test fun sameUrlAtTwoSizesAreSeparateArtworks() {
        val rec = Recorder()
        val p = prefetcher(rec)
        p.prefetch(listOf(ArtworkSpec("a", 100, 150), ArtworkSpec("a", 200, 300)))
        assertEquals(listOf("a", "a"), rec.enqueued)
        assertEquals(2, p.rememberedCount())
        p.prefetch(listOf(ArtworkSpec("a", 100, 150)))
        assertEquals(listOf("a", "a"), rec.enqueued)
    }

    @Test fun skipsNullsAndDuplicatesWithinOneBatch() {
        val rec = Recorder()
        prefetcher(rec).prefetch(specs("a", "a", "b", "b", "c"))
        assertEquals(listOf("a", "b", "c"), rec.enqueued)
    }

    @Test fun neverRequestsTheSameUrlTwice() {
        val rec = Recorder()
        val p = prefetcher(rec)
        p.prefetch(specs("a", "b"))
        p.prefetch(specs("a", "b"))
        p.prefetch(specs("b", "c"))
        assertEquals(listOf("a", "b", "c"), rec.enqueued)
        assertEquals(3, p.rememberedCount())
    }

    @Test fun capsEachBatch() {
        val rec = Recorder()
        prefetcher(rec, maxPerBatch = 3).prefetch(specs("a", "b", "c", "d", "e"))
        assertEquals(listOf("a", "b", "c"), rec.enqueued)
    }

    @Test fun newBatchCancelsPendingRequestsThatFellOutOfTheWindow() {
        val rec = Recorder()
        val p = prefetcher(rec)
        p.prefetch(specs("a", "b", "c"))
        assertEquals(3, p.pendingCount())
        p.prefetch(specs("c", "d"))
        assertTrue("a should be cancelled", rec.disposables[0].disposed)
        assertTrue("b should be cancelled", rec.disposables[1].disposed)
        assertTrue("c is still wanted, must not be cancelled", !rec.disposables[2].disposed)
    }

    @Test fun completedRequestsLeavePending() {
        val rec = Recorder()
        val p = prefetcher(rec)
        p.prefetch(specs("a", "b"))
        rec.disposables[0].deferred.cancel()
        val deadline = System.currentTimeMillis() + 2_000
        while (p.pendingCount() != 1 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(1, p.pendingCount())
    }

    @Test fun cancelAllStopsEverythingStillRunning() {
        val rec = Recorder()
        val p = prefetcher(rec)
        p.prefetch(specs("a", "b", "c"))
        assertEquals(3, p.pendingCount())
        val before = ArtworkPrefetcher.inFlightCount()
        p.cancelAll()
        assertEquals(0, p.pendingCount())
        assertTrue(rec.disposables.all { it.disposed })
        val deadline = System.currentTimeMillis() + 2_000
        while (ArtworkPrefetcher.inFlightCount() != before - 3 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals("cancelled prefetches must release the shared ceiling", before - 3, ArtworkPrefetcher.inFlightCount())
    }

    @Test fun inFlightCeilingIsSharedAcrossPrefetchers() {
        val r1 = Recorder(); val r2 = Recorder()
        val p1 = prefetcher(r1, maxPerBatch = ArtworkPrefetcher.MAX_IN_FLIGHT)
        val p2 = prefetcher(r2, maxPerBatch = ArtworkPrefetcher.MAX_IN_FLIGHT)
        p1.prefetch(specs(*(1..ArtworkPrefetcher.MAX_IN_FLIGHT).map { "a$it" }.toTypedArray()))
        p2.prefetch(specs("b1", "b2", "b3"))
        assertEquals("second prefetcher finds the ceiling full", 0, r2.enqueued.size)
        // Free one slot and the next call can use it.
        r1.disposables[0].dispose()
        val deadline = System.currentTimeMillis() + 2_000
        while (ArtworkPrefetcher.inFlightCount() != ArtworkPrefetcher.MAX_IN_FLIGHT - 1 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        p2.prefetch(specs("b1"))
        assertEquals(listOf("b1"), r2.enqueued)
    }

    @Test fun specCarriesSizeAndMemoryPolicyIntoTheRequest() {
        val sized = artworkRequest(context, ArtworkSpec("http://x/a.jpg", 280, 420))
        assertEquals(coil.size.Size(280, 420), runBlocking { sized.sizeResolver.size() })
        val diskOnly = artworkRequest(context, ArtworkSpec("http://x/a.jpg", 280, 420, memoryCache = false))
        assertEquals(CachePolicy.READ_ONLY, diskOnly.memoryCachePolicy)
    }

    @Test fun oldestUrlsAreForgottenPastTheRecentLimit() {
        val rec = Recorder()
        val p = prefetcher(rec, recentLimit = 4)
        p.prefetch(specs("a", "b", "c", "d"))
        p.prefetch(specs("e", "f"))
        assertEquals(4, p.rememberedCount())
        // "a" and "b" fell out of the window, so they may be requested again.
        p.prefetch(specs("a", "b"))
        assertTrue(rec.enqueued.count { it == "a" } == 2)
        assertTrue(rec.enqueued.count { it == "b" } == 2)
    }
}
