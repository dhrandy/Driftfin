package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.MediaUrl
import tv.jetflix.phone.data.TrailerSource
import tv.jetflix.phone.data.Trailers

class PhoneTrailersTest {
    @Test fun parsesCommonYouTubeShapes() {
        assertEquals("dQw4w9WgXcQ", Trailers.youtubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Trailers.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Trailers.youtubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Trailers.youtubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Trailers.youtubeVideoId("http://m.youtube.com/v/dQw4w9WgXcQ"))
    }

    @Test fun rejectsNonYouTubeAndBadIds() {
        assertNull(Trailers.youtubeVideoId(null))
        assertNull(Trailers.youtubeVideoId(""))
        assertNull(Trailers.youtubeVideoId("https://vimeo.com/12345"))
        assertNull(Trailers.youtubeVideoId("https://www.youtube.com/playlist?list=PLabc"))
        assertNull(Trailers.youtubeVideoId("https://www.youtube.com/watch?v=too-short"))
        assertNull(Trailers.youtubeVideoId("not a url"))
        assertNull(Trailers.youtubeVideoId("ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun serverTrailerFileWinsOverYouTube() {
        val movie = BaseItemDto(Id = "m1", Name = "Movie", RemoteTrailers = listOf(MediaUrl(Name = "YT", Url = "https://youtu.be/dQw4w9WgXcQ")))
        val local = BaseItemDto(Id = "t1", Name = "Trailer file")
        assertEquals(TrailerSource.Local(local), Trailers.pick(movie, listOf(local)))
    }

    @Test fun emptyLocalListFallsThroughToYouTube() {
        val movie = BaseItemDto(Id = "m1", Name = "Movie", RemoteTrailers = listOf(MediaUrl(Name = null, Url = "https://youtu.be/dQw4w9WgXcQ")))
        val picked = Trailers.pick(movie, emptyList())
        assertTrue(picked is TrailerSource.YouTube)
        assertEquals("dQw4w9WgXcQ", (picked as TrailerSource.YouTube).videoId)
        assertEquals("Movie trailer", picked.title)
    }

    @Test fun noTrailersAtAllGivesNull() {
        assertNull(Trailers.pick(BaseItemDto(Id = "m1", Name = "Movie"), emptyList()))
    }
}
