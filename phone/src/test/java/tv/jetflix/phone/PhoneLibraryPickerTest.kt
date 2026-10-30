package tv.jetflix.phone

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.Content
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.Sort
import tv.jetflix.phone.data.isVideoLibrary

/** The Browse library filter: which server views appear, and how a pick reaches the items query. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneLibraryPickerTest {
    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before fun setup() {
        Graph.init(ApplicationProvider.getApplicationContext())
        server = MockWebServer().also { it.start() }
        base = server.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(base, "u", "N", "t", Graph.tokens.deviceId()))
    }

    private fun enqueueItems() = server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"Items":[],"TotalRecordCount":0}"""))

    @Test fun kidsAndMixedCollectionsCountAsVideoLibraries() {
        assertTrue(isVideoLibrary(BaseItemDto(Id = "1", Name = "Movies", CollectionType = "movies")))
        assertTrue(isVideoLibrary(BaseItemDto(Id = "2", Name = "Kids", CollectionType = null))) // mixed folder libraries carry no type
        assertTrue(isVideoLibrary(BaseItemDto(Id = "3", Name = "Shows", CollectionType = "tvshows")))
        assertFalse(isVideoLibrary(BaseItemDto(Id = "4", Name = "Music", CollectionType = "music")))
        assertFalse(isVideoLibrary(BaseItemDto(Id = "5", Name = "Live TV", CollectionType = "livetv")))
        assertFalse(isVideoLibrary(BaseItemDto(Id = "6", Name = "Playlists", CollectionType = "playlists")))
        assertFalse(isVideoLibrary(BaseItemDto(Id = "7", Name = "Photos", CollectionType = "photos")))
    }

    @Test fun librariesKeepsOnlyVideoViews() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"Items":[{"Id":"m","Name":"Movies","CollectionType":"movies"},{"Id":"k","Name":"Kids"},{"Id":"mu","Name":"Music","CollectionType":"music"}]}"""))
        val libs = Content.libraries()
        assertEquals(listOf("Movies", "Kids"), libs.map { it.Name })
    }

    @Test fun browseWithLibrarySendsParentId() = runBlocking {
        enqueueItems()
        Content.browse("Movie", Sort.Added, false, null, "libKids")
        val url = server.takeRequest().path!!
        assertTrue(url, url.contains("parentId=libKids"))
        assertTrue(url, url.contains("recursive=true")) // a Kids library mixes films and shows at any depth
    }

    @Test fun browseWithoutLibrarySendsNoParentId() = runBlocking {
        enqueueItems()
        Content.browse("Series", Sort.Added, false, null, null)
        val url = server.takeRequest().path!!
        assertFalse(url, url.contains("parentId="))
    }

    @org.junit.After fun tearDown() { server.shutdown(); Graph.signOut() }
}
