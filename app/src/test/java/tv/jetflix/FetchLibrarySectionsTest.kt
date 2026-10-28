package tv.jetflix

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.ui.home.fetchLibrarySections
import java.io.IOException

/**
 * Home used to fetch its per-library rows one after another; fetchLibrarySections fetches them together
 * (bounded), keeps the view order, and keeps the old one-failure-fails-the-load behavior.
 * Timing here is runTest virtual time, so the before/after numbers are deterministic, not wall-clock luck.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FetchLibrarySectionsTest {

    private fun view(id: String, name: String) = BaseItemDto(Id = id, Name = name)

    @Test
    fun `four libraries fetch in parallel and keep view order`() = runTest {
        val views = (1..4).map { view("v$it", "Lib $it") }
        val sections = fetchLibrarySections(views) { v ->
            delay(1000)
            listOf(BaseItemDto(Id = "item-" + v.Id))
        }
        // The old sequential loop needed 4 x 1000ms; all four together need 1000ms.
        assertEquals(1000L, currentTime)
        assertEquals(listOf("Lib 1", "Lib 2", "Lib 3", "Lib 4"), sections.map { it.title })
        assertEquals(listOf("item-v1", "item-v2", "item-v3", "item-v4"), sections.map { it.items.single().Id })
        assertTrue(sections.all { !it.wide })
    }

    @Test
    fun `the same fetches sequentially cost one round trip per library - the old behavior`() = runTest {
        // Reference measurement of the pre-change loop, same fake latency.
        val views = (1..4).map { view("v$it", "Lib $it") }
        val out = mutableListOf<String>()
        for (v in views) {
            delay(1000)
            out += "item-" + v.Id
        }
        assertEquals(4000L, currentTime)
        assertEquals(listOf("item-v1", "item-v2", "item-v3", "item-v4"), out)
    }

    @Test
    fun `concurrency stays bounded - six libraries with a cap of two take three rounds`() = runTest {
        val views = (1..6).map { view("v$it", "Lib $it") }
        val sections = fetchLibrarySections(views, maxConcurrent = 2) { v ->
            delay(1000)
            listOf(BaseItemDto(Id = "item-" + v.Id))
        }
        assertEquals(3000L, currentTime)
        assertEquals((1..6).map { "Lib $it" }, sections.map { it.title })
    }

    @Test
    fun `a failing library fails the load and cancels the rest, as the sequential load did`() = runTest {
        val views = (1..3).map { view("v$it", "Lib $it") }
        var v1Cancelled = false
        try {
            fetchLibrarySections(views) { v ->
                if (v.Id == "v2") throw IOException("server error")
                try {
                    delay(1000)
                } catch (ce: CancellationException) {
                    if (v.Id == "v1") v1Cancelled = true
                    throw ce
                }
                listOf(BaseItemDto(Id = "item-" + v.Id))
            }
            fail("expected the failing library to fail the whole load")
        } catch (e: IOException) {
            assertEquals("server error", e.message)
        }
        assertTrue("sibling fetch should be cancelled, not left running", v1Cancelled)
    }

    @Test
    fun `cancelling the load cancels in-flight fetches instead of swallowing it`() = runTest {
        val views = (1..2).map { view("v$it", "Lib $it") }
        var observed: Throwable? = null
        val job = launch {
            try {
                fetchLibrarySections(views) { v ->
                    delay(5000)
                    listOf(BaseItemDto(Id = "item-" + v.Id))
                }
            } catch (t: Throwable) {
                observed = t
                throw t
            }
        }
        testScheduler.advanceTimeBy(500)
        job.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue("expected CancellationException, got $observed", observed is CancellationException)
    }

    @Test
    fun `no libraries means no rows and no work`() = runTest {
        val sections = fetchLibrarySections(emptyList()) {
            error("fetch must not run for an empty view list")
        }
        assertEquals(emptyList<Any>(), sections)
        assertEquals(0L, currentTime)
    }
}
