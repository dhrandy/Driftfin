package tv.jetflix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.app.ui.components.ArtworkPrefetcher
import tv.jetflix.app.ui.components.ArtworkSpec

/**
 * The measurable claim behind the artwork warmer: once a URL is prefetched, the request a
 * card makes when it appears is answered from Coil's cache instead of the network.
 *
 * The "before" numbers are the same run without prefetching: every card pays a network fetch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArtworkPrefetchCoilTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var loader: ImageLoader
    private var requests = 0

    private fun jpeg(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("perf/test-poster.jpg")!!.readBytes()

    @Before fun setUp() {
        requests = 0
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = synchronized(bodies) { if (bodies.isEmpty()) null else bodies.removeAt(0) }
                    ?: return MockResponse().setResponseCode(404) // never block: a cache miss must fail fast, not hang
                return MockResponse().setBody(Buffer().write(body)).setHeader("Content-Type", "image/jpeg")
            }
        }
        server.start()
        val countingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests++
                chain.proceed(chain.request())
            }
            .build()
        // Same shape as the app's loader: no HTTP cache headers honored, Coil caches itself.
        loader = ImageLoader.Builder(context)
            .okHttpClient(countingClient)
            .respectCacheHeaders(false)
            .build()
    }

    @After fun tearDown() {
        loader.shutdown()
        server.shutdown()
    }

    private val bodies = mutableListOf<ByteArray>()

    private fun serveUrls(prefix: String, count: Int): List<String> = (1..count).map { n ->
        synchronized(bodies) { bodies.add(jpeg()) }
        server.url("/$prefix/$n.jpg?maxWidth=342").toString()
    }

    /** The request shape ArtImage makes for a poster: crossfade 150, resolved to the card's pixel size. */
    private fun cardRequest(url: String): ImageRequest =
        ImageRequest.Builder(context).data(url).crossfade(150).size(280, 420).build()

    /** Same requestFor as the app. */
    private fun prefetcher() = ArtworkPrefetcher(
        requestFor = { spec -> tv.jetflix.app.ui.components.artworkRequest(context, spec) },
        enqueue = { loader.enqueue(it) },
    )

    private fun specs(urls: List<String>) = urls.map { ArtworkSpec(it) }

    private fun awaitIdle(p: ArtworkPrefetcher) {
        val deadline = System.currentTimeMillis() + 10_000
        while (p.pendingCount() > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle() // Coil posts job completion to the main looper
            Thread.sleep(20)
        }
        assertEquals("prefetches should finish", 0, p.pendingCount())
    }

    /** execute() delivers through the main looper, which Robolectric pauses; pump it until the result lands. */
    private fun executePumped(request: ImageRequest): coil.request.ImageResult {
        val deferred = GlobalScope.async(Dispatchers.IO) { loader.execute(request) }
        val deadline = System.currentTimeMillis() + 10_000
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue("image request should finish", deferred.isCompleted)
        return runBlocking { deferred.await() }
    }

    @Test(timeout = 60_000) fun withoutPrefetchEveryCardPaysANetworkFetch() {
        val urls = serveUrls("before", 5)
        urls.forEach { url -> assertTrue(executePumped(cardRequest(url)) is SuccessResult) }
        assertEquals("baseline: 5 cards, 5 network fetches", 5, requests)
    }

    @Test(timeout = 60_000) fun withPrefetchCardsAreAnsweredFromCache() {
        val urls = serveUrls("after", 4) // 4 = the per-batch cap a focus move produces
        val p = prefetcher()
        p.prefetch(specs(urls))
        awaitIdle(p)
        assertEquals("only the prefetches hit the network", 4, requests)
        val sources = urls.map { url ->
            val result = executePumped(cardRequest(url))
            assertTrue(result is SuccessResult)
            (result as SuccessResult).dataSource
        }
        assertEquals("no extra network fetches once warm", 4, requests)
        assertTrue("cards should come from memory or disk, got $sources", sources.all { it == DataSource.MEMORY_CACHE || it == DataSource.DISK })
    }

    @Test(timeout = 60_000) fun prefetchedArtHitsMemoryCacheAtTheCardsOwnSize() {
        val url = serveUrls("sized", 1).single()
        val p = prefetcher()
        p.prefetch(listOf(ArtworkSpec(url, 280, 420)))
        awaitIdle(p)
        val result = executePumped(cardRequest(url)) as SuccessResult
        assertEquals("prefetch must warm the memory cache for the card's exact request", DataSource.MEMORY_CACHE, result.dataSource)
    }

    @Test(timeout = 60_000) fun diskOnlyWarmKeepsBigArtOutOfTheStrongMemoryCache() {
        val url = serveUrls("diskonly", 1).single()
        val p = prefetcher()
        p.prefetch(listOf(ArtworkSpec(url, 280, 420, memoryCache = false)))
        awaitIdle(p)
        assertEquals(1, requests)
        assertEquals("no strong-cache write from a disk-only warm", 0, loader.memoryCache?.size)
        val result = executePumped(cardRequest(url)) as SuccessResult
        // Coil's weak memory cache may serve it if not yet collected; disk otherwise. Never the network.
        assertTrue("disk-only warm: weak memory or disk, got ${result.dataSource}", result.dataSource == DataSource.DISK || result.dataSource == DataSource.MEMORY_CACHE)
        assertEquals(1, requests)
    }

    @Test(timeout = 60_000) fun prefetchWithoutSizeOnlyWarmsDiskAndStaysUrlBounded() {
        val url = serveUrls("nosize", 1).single()
        val p = prefetcher()
        p.prefetch(listOf(ArtworkSpec(url)))
        awaitIdle(p)
        assertEquals(1, requests)
        val result = executePumped(cardRequest(url)) as SuccessResult
        // No size on the prefetch: the card's exact-size request misses memory but never the network...
        assertEquals(DataSource.DISK, result.dataSource)
        // ...and the decoded bitmap stays bounded by the URL's maxWidth, not the display size.
        val bitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap
        assertTrue("bitmap ${bitmap.width}x${bitmap.height} should stay near the 342px source", bitmap.width <= 342 && bitmap.height <= 513)
    }
}
