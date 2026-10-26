package tv.jetflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.actor.ActorViewModel
import tv.jetflix.app.ui.actor.cleanBio

@OptIn(ExperimentalCoroutinesApi::class)
class ActorViewModelTest {
    private lateinit var server: MockWebServer
    private val requests = mutableListOf<RecordedRequest>()
    private var personBody = """{"Id":"p1","Name":"Pat Example","Overview":"  Born somewhere.  ","ImageTags":{"Primary":"tg"}}"""
    private var itemsBody = """{"Items":[{"Id":"m1","Name":"First"},{"Id":"m2","Name":"Second"}],"TotalRecordCount":2}"""
    private var itemsCode = 200

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.path.orEmpty()
                    return if (path.contains("/Items?") || path.startsWith("/Items?")) MockResponse().setResponseCode(itemsCode).setBody(itemsBody)
                    else MockResponse().setBody(personBody)
                }
            }
            start()
        }
    }
    @After fun tearDown() { server.shutdown(); SessionManager.signOut(); Dispatchers.resetMain() }

    /** Network answers arrive on real threads; pump the test dispatcher until they land (or 2 s pass). */
    private fun kotlinx.coroutines.test.TestScope.settle(vm: ActorViewModel, untilLoaded: Boolean = true) {
        repeat(40) { advanceUntilIdle(); if (untilLoaded && !vm.state.value.loading) return; Thread.sleep(50) }
        advanceUntilIdle()
    }

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun signIn(user: String, token: String) = SessionManager.signIn(Session(base(), user, "n$user", token, "dev"))

    @Test fun loadsMoviesAndBioForTheSignedInUserOnly() = runTest {
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository())
        vm.load("p1", "Pat")
        settle(vm)
        val st = vm.state.value
        assertEquals(listOf("m1", "m2"), st.movies.map { it.Id })
        assertEquals("Born somewhere.", st.bio)
        assertEquals("Pat Example", st.name)
        val listReq = requests.first { it.path!!.contains("personIds=p1") }
        assertTrue(listReq.path!!.contains("userId=u1"))
        assertTrue(listReq.path!!.contains("includeItemTypes=Movie"))
        assertTrue(listReq.getHeader("Authorization")!!.contains("Token=\"tok1\""))
        assertTrue(requests.none { it.method != "GET" })
    }

    @Test fun missingBioIsNullNotInvented() = runTest {
        personBody = """{"Id":"p1","Name":"Pat Example"}"""
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository()); vm.load("p1", "Pat"); settle(vm)
        assertNull(vm.state.value.bio)
        assertNull(vm.state.value.photoUrl)
        assertEquals(2, vm.state.value.movies.size)
    }

    @Test fun blankBioIsNull() { assertNull(cleanBio("  \n ")); assertNull(cleanBio(null)) }

    @Test fun emptyCreditsGiveEmptyListNoError() = runTest {
        itemsBody = """{"Items":[],"TotalRecordCount":0}"""
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository()); vm.load("p1", "Pat"); settle(vm)
        assertTrue(vm.state.value.movies.isEmpty()); assertNull(vm.state.value.error); assertFalse(vm.state.value.loading)
    }

    @Test fun failureShowsErrorAndRetryReloads() = runTest {
        itemsCode = 500
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository()); vm.load("p1", "Pat"); settle(vm)
        assertTrue(vm.state.value.error != null)
        itemsCode = 200
        vm.retry("p1", "Pat"); settle(vm)
        assertNull(vm.state.value.error); assertEquals(2, vm.state.value.movies.size)
    }

    @Test fun answerForAPreviousProfileIsDroppedAfterASwitch() = runTest {
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository())
        vm.load("p1", "Pat")          // launched but not run yet
        signIn("u2", "tok2")          // switch profile before the answer lands
        settle(vm, untilLoaded = false)
        assertTrue("old profile's movies must not appear", vm.state.value.movies.isEmpty())
    }

    @Test fun sameUserIdOnAnotherServerIsADifferentSession() = runTest {
        signIn("u1", "tok1")
        val vm = ActorViewModel(JellyfinRepository())
        vm.load("p1", "Pat")
        SessionManager.signIn(Session("http://other.test:8096", "u1", "nu1", "tok1", "dev"))
        settle(vm, untilLoaded = false)
        assertTrue(vm.state.value.movies.isEmpty())
    }
}
