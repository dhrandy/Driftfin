package tv.jetflix

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.GuestBioResolver

/**
 * The id-keyed biography chain: a TVMaze person id must resolve through Wikidata's "TV Maze person
 * ID" statement (P11449) to exactly one item, then to its English Wikipedia sitelink summary.
 * Every failure shape yields null (no bio), never a guess: the panel then shows only the verified
 * episode's own data.
 */
class GuestBioTest {
    private lateinit var wikidata: MockWebServer
    private lateinit var wikipedia: MockWebServer

    @Before fun setUp() {
        wikidata = MockWebServer().apply { start() }
        wikipedia = MockWebServer().apply { start() }
    }
    @After fun tearDown() { wikidata.shutdown(); wikipedia.shutdown() }

    private fun resolver() = GuestBioResolver(
        GuestBioResolver.wikidataFor(wikidata.url("/").toString()),
        GuestBioResolver.wikipediaFor(wikipedia.url("/").toString()),
    )

    private fun searchBody(vararg qids: String) =
        qids.joinToString(",", """{"query":{"search":[""", "]}}") { """{"title":"$it"}""" }
    private fun entitiesBody(qid: String, title: String?) =
        if (title == null) """{"entities":{"$qid":{"sitelinks":{}}}}"""
        else """{"entities":{"$qid":{"sitelinks":{"enwiki":{"title":"$title"}}}}}"""

    @Test fun provenChainReturnsTheWikipediaExtract() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody("Q117083352")))
        wikidata.enqueue(MockResponse().setBody(entitiesBody("Q117083352", "Lisa Gilroy")))
        wikipedia.enqueue(MockResponse().setBody("""{"title":"Lisa Gilroy","extract":"Lisa Gilroy is a Canadian actress and comedian."}"""))
        assertEquals("Lisa Gilroy is a Canadian actress and comedian.", resolver().bioFor("187924"))
        // Exactly one lookup per service, and the search carried the id, never a name.
        assertEquals(2, wikidata.requestCount)
        assertEquals(1, wikipedia.requestCount)
        val search = wikidata.takeRequest()
        check(search.path!!.contains("haswbstatement%3AP11449%3D187924") || search.path!!.contains("haswbstatement:P11449=187924")) { search.path!! }
    }

    @Test fun noStatementMeansNoBio() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody()))
        assertNull(resolver().bioFor("999999"))
        assertEquals(1, wikidata.requestCount)
        assertEquals(0, wikipedia.requestCount)
    }

    @Test fun twoItemsWithTheStatementAreAmbiguousAndYieldNoBio() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody("Q1", "Q2")))
        assertNull(resolver().bioFor("187924"))
        assertEquals(0, wikipedia.requestCount)
    }

    @Test fun missingEnglishSitelinkYieldsNoBio() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody("Q5")))
        wikidata.enqueue(MockResponse().setBody(entitiesBody("Q5", null)))
        assertNull(resolver().bioFor("187924"))
        assertEquals(0, wikipedia.requestCount)
    }

    @Test fun summaryFailureYieldsNoBio() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody("Q5")))
        wikidata.enqueue(MockResponse().setBody(entitiesBody("Q5", "Some Person")))
        wikipedia.enqueue(MockResponse().setResponseCode(404))
        assertNull(resolver().bioFor("187924"))
    }

    @Test fun blankExtractYieldsNoBio() = runTest {
        wikidata.enqueue(MockResponse().setBody(searchBody("Q5")))
        wikidata.enqueue(MockResponse().setBody(entitiesBody("Q5", "Some Person")))
        wikipedia.enqueue(MockResponse().setBody("""{"title":"Some Person","extract":"  "}"""))
        assertNull(resolver().bioFor("187924"))
    }

    @Test fun nonNumericIdNeverTouchesTheNetwork() = runTest {
        assertNull(resolver().bioFor("not-an-id"))
        assertEquals(0, wikidata.requestCount)
        assertEquals(0, wikipedia.requestCount)
    }

    @Test fun successfulBioIsCachedPerPerson() = runTest {
        val r = resolver()
        wikidata.enqueue(MockResponse().setBody(searchBody("Q5")))
        wikidata.enqueue(MockResponse().setBody(entitiesBody("Q5", "Some Person")))
        wikipedia.enqueue(MockResponse().setBody("""{"title":"Some Person","extract":"A real person."}"""))
        assertEquals("A real person.", r.bioFor("7"))
        assertEquals("A real person.", r.bioFor("7"))
        assertEquals(2, wikidata.requestCount)
        assertEquals(1, wikipedia.requestCount)
    }

    @Test fun failuresAreNotCachedSoATransientErrorDoesNotSuppressABioForever() = runTest {
        val r = resolver()
        wikidata.enqueue(MockResponse().setResponseCode(429))
        wikidata.enqueue(MockResponse().setBody(searchBody()))
        assertNull(r.bioFor("42"))
        assertNull(r.bioFor("42"))
        assertEquals(2, wikidata.requestCount) // the second open retried
    }

    @Test fun cancellingALookupLeavesNothingCached() = kotlinx.coroutines.runBlocking {
        val r = resolver()
        wikidata.enqueue(MockResponse().setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS).setBody(searchBody()))
        var cancelled = false
        val job = kotlinx.coroutines.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { r.bioFor("9") } catch (ce: kotlinx.coroutines.CancellationException) { cancelled = true }
        }
        kotlinx.coroutines.delay(500)
        job.cancel()
        job.join()
        org.junit.Assert.assertTrue("the in-flight lookup observed its cancellation", cancelled)
        // The cancelled lookup cached nothing: the next open hits the network again and can succeed.
        wikidata.enqueue(MockResponse().setBody(searchBody()))
        assertNull(r.bioFor("9"))
        assertEquals(2, wikidata.requestCount)
    }
}
