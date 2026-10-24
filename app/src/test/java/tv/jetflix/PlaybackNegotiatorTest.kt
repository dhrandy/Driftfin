package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.MediaSourceInfo
import tv.jetflix.app.data.PlaybackChoice
import tv.jetflix.app.data.PlaybackInfoResponse
import tv.jetflix.app.data.PlaybackNegotiator

class PlaybackNegotiatorTest {
    private val base = "http://s"

    @Test fun prefersDirectPlay() {
        val r = PlaybackInfoResponse(listOf(MediaSourceInfo(Id = "m1", SupportsDirectPlay = true)), "ps")
        val c = PlaybackNegotiator.choose(r, base, "i1") as PlaybackChoice.Direct
        assertEquals("http://s/Videos/i1/stream?static=true&mediaSourceId=m1", c.url)
    }

    @Test fun fallsBackToHlsTranscode() {
        val r = PlaybackInfoResponse(listOf(MediaSourceInfo(Id = "m1", SupportsDirectPlay = false, SupportsTranscoding = true, TranscodingUrl = "/videos/i1/master.m3u8?x=1")), "ps")
        val c = PlaybackNegotiator.choose(r, base, "i1") as PlaybackChoice.Hls
        assertEquals("http://s/videos/i1/master.m3u8?x=1", c.url)
    }

    @Test fun reportsUnplayableInsteadOfGuessing() {
        assertTrue(PlaybackNegotiator.choose(null, base, "i1") is PlaybackChoice.Unplayable)
        val noWay = PlaybackInfoResponse(listOf(MediaSourceInfo(Id = "m1", SupportsDirectPlay = false, SupportsTranscoding = false)), "ps")
        assertTrue(PlaybackNegotiator.choose(noWay, base, "i1") is PlaybackChoice.Unplayable)
    }

    @Test fun profileDeclaresHlsTranscodeAndDirectPlay() {
        val body = PlaybackNegotiator.playbackInfoBody("u1", 0, tv.jetflix.app.data.CodecSupport(listOf("h264", "hevc"), listOf("aac", "ac3"))).toString()
        assertTrue(body.contains("\"EnableTranscoding\":true") && body.contains("hls") && body.contains("hevc"))
    }

    @Test fun profileOnlyAdvertisesCodecsTheDeviceReports() {
        val body = PlaybackNegotiator.playbackInfoBody("u1", 0, tv.jetflix.app.data.CodecSupport(listOf("h264"), listOf("aac"))).toString()
        assertTrue(!body.contains("hevc") && !body.contains("vp9") && !body.contains("eac3"))
    }
}

class ExternalSubtitleTest {
    private fun stream(i: Int, codec: String, method: String, ext: Boolean, url: String? = "/Videos/x/y/Subtitles/$i/0/Stream.$codec") =
        tv.jetflix.app.data.MediaStreamInfo(Index = i, Type = "Subtitle", Codec = codec, DeliveryMethod = method, DeliveryUrl = url, IsExternal = ext, Language = "eng", DisplayTitle = "English")

    private val src = tv.jetflix.app.data.MediaSourceInfo(
        Id = "y",
        MediaStreams = listOf(
            stream(2, "srt", "External", false),
            stream(3, "srt", "External", true),
            stream(4, "pgssub", "Drop", false, null),
            stream(5, "ass", "External", false),
        ),
    )

    @org.junit.Test fun directPlayAddsOnlySidecarFiles() {
        val t = tv.jetflix.app.data.PlaybackNegotiator.externalSubtitles(src, "http://s", directPlay = true)
        org.junit.Assert.assertEquals(listOf("http://s/Videos/x/y/Subtitles/3/0/Stream.srt"), t.map { it.url })
        org.junit.Assert.assertEquals("application/x-subrip", t[0].mimeType)
    }

    @org.junit.Test fun transcodeAddsEveryTextTrackButNotPictureOnes() {
        val t = tv.jetflix.app.data.PlaybackNegotiator.externalSubtitles(src, "http://s", directPlay = false)
        org.junit.Assert.assertEquals(3, t.size)
        org.junit.Assert.assertTrue(t.any { it.mimeType == "text/x-ssa" })
    }

    @org.junit.Test fun profileOffersTextFormatsOnly() {
        val s = tv.jetflix.app.data.PlaybackNegotiator.deviceProfile().toString()
        org.junit.Assert.assertTrue(s.contains("\"Format\":\"srt\""))
        org.junit.Assert.assertFalse(s.contains("pgs"))
    }
}
