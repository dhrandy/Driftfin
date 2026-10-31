package tv.jetflix

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.AuthenticateUserByName
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.msToTicks
import tv.jetflix.app.data.ticksToHms
import tv.jetflix.app.data.ticksToMs

class JellyfinRepositoryTest {
    private lateinit var server: MockWebServer
    private val repo = JellyfinRepository()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown(); SessionManager.signOut() }

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test fun normalizesServerInput() {
        assertEquals("http://host:8096", repo.normalizeServerUrl(" host:8096/ "))
        assertEquals("https://x.example", repo.normalizeServerUrl("https://x.example/web/"))
    }

    @Test fun authSendsAuthorizationHeaderNotLegacy() = runTest {
        server.enqueue(MockResponse().setBody("""{"AccessToken":"tok","User":{"Id":"u1","Name":"n"},"Extra":1}"""))
        val res = repo.apiFor(base()).authenticateByName(AuthenticateUserByName("n", "p"))
        assertTrue(res.isSuccessful)
        assertEquals("tok", res.body()?.AccessToken)
        val req = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("MediaBrowser Client=\"Driftfin\""))
        assertNull(req.getHeader("X-Emby-Authorization"))
        assertFalse(req.getHeader("Authorization")!!.contains("Token="))
    }

    @Test fun tokenAddedAfterSignIn() = runTest {
        SessionManager.signIn(tv.jetflix.app.data.Session(base(), "u1", "n", "secret-token", "dev"))
        server.enqueue(MockResponse().setBody("""{"Items":[],"TotalRecordCount":0}"""))
        repo.apiFor(base()).getUserViews("u1")
        val req = server.takeRequest()
        assertEquals("/UserViews?userId=u1", req.path)
        assertTrue(req.getHeader("Authorization")!!.contains("Token=\"secret-token\""))
    }

    @Test fun filterOptionsComeFromTheServerWholeLibraryList() = runTest {
        SessionManager.signIn(tv.jetflix.app.data.Session(base(), "u1", "n", "t", "dev"))
        server.enqueue(MockResponse().setBody("""{"Genres":["Western","Action"],"Tags":[],"OfficialRatings":[],"Years":[1990,2001,1995],"Extra":1}"""))
        val f = repo.apiFor(base()).getItemFilters("u1", "lib1", "Movie").body()!!
        assertEquals(listOf("Western", "Action"), f.Genres)
        assertEquals(listOf(1990, 2001, 1995), f.Years)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/Items/Filters?") && path.contains("parentId=lib1") && path.contains("includeItemTypes=Movie"))
    }

    @Test fun badCredentialsReturnUnsuccessfulNotCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val res = repo.apiFor(base()).authenticateByName(AuthenticateUserByName("n", "bad"))
        assertFalse(res.isSuccessful)
        assertEquals(401, res.code())
    }

    @Test fun serverErrorIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val res = repo.apiFor(base()).getResumeItems("u1")
        assertEquals(500, res.code())
    }

    @Test fun streamAndImageUrls() = runTest {
        repo.apiFor(base())
        assertTrue(repo.streamUrl("i1").contains("/Videos/i1/stream?static=true"))
        assertTrue(repo.imageUrl("i1", "Primary", 300, "t").contains("/Items/i1/Images/Primary"))
    }

    @Test fun imageUrlsRequestWebp() = runTest {
        // Artwork goes out as WebP: far smaller than the default JPEG at poster sizes.
        repo.apiFor(base())
        assertEquals(
            base() + "/Items/i1/Images/Primary?maxWidth=342&tag=t&format=Webp&quality=85",
            repo.imageUrl("i1", "Primary", 342, "t"),
        )
        assertEquals(
            base() + "/Items/i1/Images/Backdrop/0?maxWidth=1280&format=Webp&quality=85",
            repo.imageUrl("i1", "Backdrop", 1280, null, 0),
        )
        // Logos deliberately keep their original URL shape: they carry transparency, and a
        // server without a WebP encoder would flatten a requested WebP to JPEG.
        assertEquals(
            base() + "/Items/i1/Images/Logo?maxWidth=600",
            repo.imageUrl("i1", "Logo", 600),
        )
    }

    @Test fun itemLabels() {
        val ep = BaseItemDto(Type = "Episode", SeriesName = "Show", ParentIndexNumber = 2, IndexNumber = 5, RunTimeTicks = 36_000_000_000)
        assertEquals("Show", ep.displayTitle())
        assertEquals("S2:E5", ep.episodeLabel())
        assertEquals("1h 0m", ep.runtimeLabel())
        assertEquals("1:05:09", 39_090_000_000L.ticksToHms())
    }

    @Test fun resumeTicksConvertToMilliseconds() {
        // 90 seconds in Jellyfin ticks must start ExoPlayer at 90,000 ms, not 900,000,000.
        assertEquals(90_000L, 900_000_000L.ticksToMs())
        assertEquals(900_000_000L, 90_000L.msToTicks())
    }

    @Test fun explicitLoginIdentityNeverCarriesOldSessionTokenAndCacheTracksIdentity() = runTest {
        SessionManager.signIn(tv.jetflix.app.data.Session("https://old.invalid", "old", "old", "old-secret", "old-device"))
        for (id in listOf("install-A", "install-A", "install-B")) {
            server.enqueue(MockResponse().setBody("{}"))
            repo.apiFor(base(), deviceId = id).getPublicSystemInfo()
            val auth = server.takeRequest().getHeader("Authorization")!!
            assertTrue(auth.contains("DeviceId=\"$id\""))
            assertFalse(auth.contains("Token="))
            assertFalse(auth.contains("old-secret"))
        }
    }
}
