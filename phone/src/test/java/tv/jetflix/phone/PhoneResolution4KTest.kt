package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.MediaSourceInfo
import tv.jetflix.phone.data.MediaStreamInfo
import tv.jetflix.phone.data.Resolution4K

class PhoneResolution4KTest {
    private fun video(w: Int?, h: Int?) = BaseItemDto(
        Id = "x", Type = "Movie",
        MediaSources = listOf(MediaSourceInfo(MediaStreams = listOf(
            MediaStreamInfo(Type = "Audio"), MediaStreamInfo(Type = "Video", Width = w, Height = h),
        ))),
    )
    private fun episode(w: Int?, h: Int?) = video(w, h).copy(Type = "Episode")

    @Test fun absentSourcesAreUnknown() {
        assertNull(Resolution4K.of(BaseItemDto(Id = "x", Type = "Movie")))
        assertFalse(Resolution4K.badge(BaseItemDto(Id = "x", Type = "Movie")))
    }

    @Test fun missingOrZeroSizeIsUnknown() {
        assertNull(Resolution4K.of(video(null, null)))
        assertNull(Resolution4K.of(video(0, 0)))
        assertNull(Resolution4K.of(BaseItemDto(Id = "x", MediaSources = listOf(MediaSourceInfo(MediaStreams = listOf(MediaStreamInfo(Type = "Audio")))))))
    }

    @Test fun notFourK() {
        assertEquals(false, Resolution4K.of(video(1920, 1080)))
        assertEquals(false, Resolution4K.of(video(2560, 1440)))
        assertFalse(Resolution4K.badge(video(1920, 1080)))
    }

    @Test fun fourKByWidthOrHeight() {
        assertEquals(true, Resolution4K.of(video(3840, 2160)))
        assertEquals(true, Resolution4K.of(video(3840, 1600)))   // cinemascope crop, still 4K
        assertEquals(true, Resolution4K.of(video(3000, 2160)))
        assertTrue(Resolution4K.badge(video(3840, 2160)))
    }

    private fun twoSources(a: Pair<Int, Int>, b: Pair<Int, Int>) = BaseItemDto(
        Id = "x", Type = "Movie",
        MediaSources = listOf(a, b).map { (w, h) -> MediaSourceInfo(MediaStreams = listOf(MediaStreamInfo(Type = "Video", Width = w, Height = h))) },
    )

    @Test fun anyVersionBeingFourKCounts() {
        assertEquals(true, Resolution4K.of(twoSources(1920 to 1080, 3840 to 2160)))
        assertEquals(true, Resolution4K.of(twoSources(3840 to 2160, 1920 to 1080)))
        assertEquals(false, Resolution4K.of(twoSources(1920 to 1080, 1280 to 720)))
        assertNull("a version with no size is skipped, not counted", Resolution4K.of(twoSources(0 to 0, 0 to 0)))
        assertEquals(false, Resolution4K.of(twoSources(0 to 0, 1920 to 1080)))
    }

    @Test fun onlyMoviesUseTheSourceRule() {
        assertTrue(Resolution4K.badge(video(3840, 2160)))
        assertFalse("episodes badge from the server's 4K list instead", Resolution4K.badge(episode(3840, 2160)))
        assertFalse("show", Resolution4K.badge(BaseItemDto(Id = "s", Type = "Series")))
    }
}
