package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.MediaUrl
import tv.jetflix.app.data.TrailerSource
import tv.jetflix.app.data.Trailers
import tv.jetflix.app.ui.player.YouTubeTrailerPage

class TrailersTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun readsCommonYouTubeLinkShapes() {
        assertEquals(id, Trailers.youtubeVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, Trailers.youtubeVideoId("http://youtube.com/watch?v=$id&t=5"))
        assertEquals(id, Trailers.youtubeVideoId("https://youtu.be/$id?si=abc"))
        assertEquals(id, Trailers.youtubeVideoId("https://www.youtube.com/embed/$id"))
        assertEquals(id, Trailers.youtubeVideoId("https://m.youtube.com/shorts/$id"))
    }

    @Test fun rejectsOtherSitesBadIdsAndInjection() {
        assertNull(Trailers.youtubeVideoId(null))
        assertNull(Trailers.youtubeVideoId("https://vimeo.com/12345678901"))
        assertNull(Trailers.youtubeVideoId("https://evil.example/watch?v=$id"))
        assertNull(Trailers.youtubeVideoId("https://www.youtube.com/playlist?list=PL123"))
        assertNull(Trailers.youtubeVideoId("https://www.youtube.com/watch?v=short"))
        assertNull(Trailers.youtubeVideoId("https://www.youtube.com/watch?v=$id');alert(1);//"))
        assertNull(Trailers.youtubeVideoId("javascript:alert(1)"))
    }

    @Test fun serverTrailerFilesComeBeforeYouTubeLinks() {
        val item = BaseItemDto(Id = "m1", Name = "Movie", RemoteTrailers = listOf(MediaUrl("T", "https://youtu.be/$id")))
        val local = BaseItemDto(Id = "t1", Name = "Trailer", Type = "Trailer")
        assertEquals(TrailerSource.Local(local), Trailers.pick(item, listOf(local)))
        assertEquals(TrailerSource.YouTube(id, "T"), Trailers.pick(item, emptyList()))
    }

    @Test fun noPlayableTrailerHidesTheButton() {
        val item = BaseItemDto(Id = "m1", Name = "Movie", RemoteTrailers = listOf(MediaUrl("x", "https://vimeo.com/1")))
        assertNull(Trailers.pick(item, emptyList()))
        assertNull(Trailers.pick(BaseItemDto(Id = "m2"), emptyList()))
    }

    @Test fun unnamedRemoteTrailerGetsATitle() {
        val item = BaseItemDto(Id = "m1", Name = "Movie", RemoteTrailers = listOf(MediaUrl(null, "https://youtu.be/$id")))
        assertEquals(TrailerSource.YouTube(id, "Movie trailer"), Trailers.pick(item, emptyList()))
    }

    @Test fun pageIdentifiesTheAppAndRejectsBadIds() {
        val origin = YouTubeTrailerPage.origin("tv.Jetflix.app")
        assertEquals("https://tv.jetflix.app", origin)
        val html = YouTubeTrailerPage.html(id, origin)
        assertTrue(html.contains("videoId:'$id'"))
        assertTrue(html.contains("origin:'https://tv.jetflix.app'"))
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        var threw = false
        try { YouTubeTrailerPage.html("x');alert(1);//", origin) } catch (_: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }

    @Test fun errorCodesGetPlainMessages() {
        assertTrue(YouTubeTrailerPage.errorMessage(153).contains("identity"))
        assertTrue(YouTubeTrailerPage.errorMessage(101).contains("doesn't allow"))
        assertTrue(YouTubeTrailerPage.errorMessage(999).contains("999"))
    }
}
