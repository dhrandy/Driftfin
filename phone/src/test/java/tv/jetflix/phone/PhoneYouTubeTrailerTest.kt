package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.ui.YouTubeTrailerPage

class PhoneYouTubeTrailerTest {
    @Test fun originIsHttpsLowercasePackage() {
        assertEquals("https://tv.jetflix.phone", YouTubeTrailerPage.origin("tv.jetflix.phone"))
        assertEquals("https://tv.jetflix.phone", YouTubeTrailerPage.origin("TV.JETFLIX.PHONE"))
    }

    @Test fun htmlEmbedsValidatedIdAndOrigin() {
        val html = YouTubeTrailerPage.html("dQw4w9WgXcQ", "https://tv.jetflix.phone")
        assertTrue(html.contains("videoId:'dQw4w9WgXcQ'"))
        assertTrue(html.contains("origin:'https://tv.jetflix.phone'"))
        assertThrows(IllegalArgumentException::class.java) { YouTubeTrailerPage.html("bad');alert(1);//", "https://tv.jetflix.phone") }
        assertThrows(IllegalArgumentException::class.java) { YouTubeTrailerPage.html("short", "https://tv.jetflix.phone") }
    }

    @Test fun errorMessagesArePlainLanguage() {
        assertEquals("This trailer link isn't valid.", YouTubeTrailerPage.errorMessage(2))
        assertEquals("This trailer was removed or is private.", YouTubeTrailerPage.errorMessage(100))
        assertEquals("The owner doesn't allow this trailer to play outside YouTube.", YouTubeTrailerPage.errorMessage(101))
        assertEquals("The owner doesn't allow this trailer to play outside YouTube.", YouTubeTrailerPage.errorMessage(150))
        assertEquals("YouTube didn't accept this app's identity for embedded playback.", YouTubeTrailerPage.errorMessage(153))
        assertEquals("This trailer can't be played here (error 999).", YouTubeTrailerPage.errorMessage(999))
    }
}
