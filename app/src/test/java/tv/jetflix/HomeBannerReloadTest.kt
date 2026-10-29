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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.home.HomeViewModel

/** Reload of Home (what the animation toggle triggers) with the banner request failing on the server side. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeBannerReloadTest {
    private lateinit var server: MockWebServer
    @Volatile private var bannerCode = 200
    @Volatile private var bannerMovies: String? = null
    private val bannerRequests = java.util.concurrent.atomic.AtomicInteger()
    private val movie = """{"Id":"m1","Name":"Banner Movie","Type":"Movie","CommunityRating":8.0,"BackdropImageTags":["t"],"Genres":["Drama"]}"""

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val p = request.path.orEmpty()
                    return when {
                        p.contains("IsUnplayed") -> { bannerRequests.incrementAndGet(); MockResponse().setResponseCode(bannerCode).setBody(if (bannerCode == 200) """{"Items":[${bannerMovies ?: movie}],"TotalRecordCount":1}""" else "") }
                        p.contains("/Latest") -> MockResponse().setBody("[]")
                        else -> MockResponse().setBody("""{"Items":[$movie],"TotalRecordCount":1}""")
                    }
                }
            }
            start()
        }
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u1", "nu1", "tok", "dev"))
    }
    @After fun tearDown() { server.shutdown(); SessionManager.signOut(); AppSettings.setExcludeHomeAnimation(false); Dispatchers.resetMain() }

    private fun kotlinx.coroutines.test.TestScope.settle(vm: HomeViewModel) {
        repeat(60) { advanceUntilIdle(); if (!vm.state.value.loading) { Thread.sleep(100); advanceUntilIdle(); return }; Thread.sleep(50) }
    }

    private fun kotlinx.coroutines.test.TestScope.reload(vm: HomeViewModel, expectedBannerRequests: Int) {
        vm.load()
        repeat(80) { advanceUntilIdle(); if (bannerRequests.get() >= expectedBannerRequests) return@repeat; Thread.sleep(50) }
        Thread.sleep(500); advanceUntilIdle()
        assertEquals(expectedBannerRequests, bannerRequests.get())
    }
    private val animated = """{"Id":"a1","Name":"Cartoon","Type":"Movie","CommunityRating":9.0,"BackdropImageTags":["t"],"Genres":["Animation"]}"""

    @Test fun failedReloadWithAnimationOffNeverKeepsAnimatedTitleAndMixedListIsFiltered() = runTest {
        bannerMovies = "$animated,$movie"
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertEquals(setOf("a1", "m1"), vm.state.value.heroes.map { it.Id }.toSet())
        bannerCode = 500
        AppSettings.setExcludeHomeAnimation(true)
        reload(vm, 2)
        assertEquals(listOf("m1"), vm.state.value.heroes.map { it.Id })
        assertEquals("m1", vm.state.value.hero?.Id)
    }

    @Test fun failedReloadWhereEveryKeptTitleIsAnimatedShowsNoBanner() = runTest {
        bannerMovies = animated
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        bannerCode = 500
        AppSettings.setExcludeHomeAnimation(true)
        reload(vm, 2)
        assertEquals(null, vm.state.value.hero)
    }

    private val started = """{"Id":"s1","Name":"Half Watched","Type":"Movie","CommunityRating":8.5,"BackdropImageTags":["t"],"Genres":["Drama"],"UserData":{"PlaybackPositionTicks":5000000,"Played":false}}"""

    @Test fun animationFilterEmptyingTheUntouchedPoolFallsBackToStartedTitles() = runTest {
        // Todd's row-86 repro: a successful load, animation excluded, and the only non-animated titles are
        // started-but-unfinished. The banner must relax the untouched gate instead of vanishing.
        AppSettings.setExcludeHomeAnimation(true)
        bannerMovies = "$animated,$started"
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertEquals("s1", vm.state.value.hero?.Id)
        assertEquals(listOf("s1"), vm.state.value.heroes.map { it.Id })
    }

    @Test fun untouchedTitlesStillWinOverStartedOnes() = runTest {
        AppSettings.setExcludeHomeAnimation(true)
        bannerMovies = "$animated,$started,$movie"
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertEquals("m1", vm.state.value.hero?.Id)
        assertEquals(listOf("m1"), vm.state.value.heroes.map { it.Id })
    }

    @Test fun successfulEmptyAnswerClearsTheBanner() = runTest {
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertNotNull(vm.state.value.hero)
        bannerMovies = ""
        reload(vm, 2)
        assertEquals(null, vm.state.value.hero)
    }

    @Test fun failedReloadForAnotherUserNeverKeepsTheFirstUsersBanner() = runTest {
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertNotNull(vm.state.value.hero)
        bannerCode = 500
        SessionManager.signIn(Session(server.url("/").toString().trimEnd('/'), "u2", "nu2", "tok2", "dev"))
        reload(vm, 2)
        assertEquals(null, vm.state.value.hero)
        assertEquals(emptyList<String>(), vm.state.value.heroes.map { it.Id })
    }

    @Test fun bannerSurvivesAReloadWhoseBannerRequestFails() = runTest {
        val vm = HomeViewModel(JellyfinRepository())
        vm.load(); settle(vm)
        assertNotNull("first load shows a banner", vm.state.value.hero)
        bannerCode = 500
        AppSettings.setExcludeHomeAnimation(true)
        vm.load()
        repeat(80) { advanceUntilIdle(); if (bannerRequests.get() >= 2) return@repeat; Thread.sleep(50) }
        Thread.sleep(500); advanceUntilIdle()
        assertEquals("reload asked for the banner again", 2, bannerRequests.get())
        assertEquals("a failed banner request must not blank the banner", "m1", vm.state.value.hero?.Id)
        assertEquals("banner request failed; kept=1", vm.state.value.bannerDiag)
    }
}
