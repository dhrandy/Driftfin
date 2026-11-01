package tv.jetflix.phone

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.ui.HomeData
import tv.jetflix.phone.ui.quietRefreshed

/** Phone Home quiet refresh: only Continue Watching and Next Up change, and never from a failed or identical answer. */
@RunWith(RobolectricTestRunner::class)
class PhoneQuietRefreshTest {
    private lateinit var server: MockWebServer
    private var code = 200

    @Before fun up() {
        SessionManager.signOut()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (code != 200) MockResponse().setResponseCode(code)
                    else MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[{"Id":"e1","Name":"Ep","Type":"Episode"}]}""")
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "Alex", "token", "dev"))
    }

    @After fun down() { server.shutdown(); SessionManager.signOut() }

    private fun item(id: String) = BaseItemDto(Id = id)

    @Test fun failedRequestIsNullNotEmpty() = runBlocking {
        code = 500
        assertNull(Content.resumeOrNull()); assertNull(Content.nextUpOrNull())
        code = 200
        assertEquals(listOf("e1"), Content.resumeOrNull()?.map { it.Id })
    }

    @Test fun replacesOnlyTheTwoRows() {
        val cur = HomeData(heroes = listOf(item("h")), resume = listOf(item("a")), nextUp = listOf(item("n")), movies = listOf(item("m")), loaded = true)
        val out = quietRefreshed(cur, listOf(item("b"), item("a")), listOf(item("n")))!!
        assertEquals(listOf("b", "a"), out.resume.map { it.Id })
        assertSame(cur.heroes, out.heroes); assertSame(cur.movies, out.movies)
    }

    @Test fun nothingChangesForTheSameAnswerAFailureOrBeforeTheFirstLoad() {
        val cur = HomeData(resume = listOf(item("a")), nextUp = listOf(item("n")), loaded = true)
        assertNull(quietRefreshed(cur, listOf(item("a")), listOf(item("n"))))
        assertNull(quietRefreshed(cur, null, listOf(item("n"))))
        assertNull(quietRefreshed(cur, listOf(item("a")), null))
        assertNull(quietRefreshed(cur.copy(loaded = false), listOf(item("z")), listOf(item("n"))))
    }

    @Test fun anEmptyRowFromASuccessfulAnswerIsApplied() {
        val cur = HomeData(resume = listOf(item("a")), loaded = true)
        assertEquals(emptyList<BaseItemDto>(), quietRefreshed(cur, emptyList(), emptyList())!!.resume)
    }
}
