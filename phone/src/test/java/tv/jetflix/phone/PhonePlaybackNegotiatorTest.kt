package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.phone.data.MediaSourceInfo
import tv.jetflix.phone.data.PlaybackChoice
import tv.jetflix.phone.data.PlaybackInfoResponse
import tv.jetflix.phone.data.PlaybackNegotiator

class PhonePlaybackNegotiatorTest {
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
        val body = PlaybackNegotiator.playbackInfoBody("u1", 0, tv.jetflix.phone.data.CodecSupport(listOf("h264", "hevc"), listOf("aac", "ac3"))).toString()
        assertTrue(body.contains("\"EnableTranscoding\":true") && body.contains("hls") && body.contains("hevc"))
    }

    @Test fun profileOnlyAdvertisesCodecsTheDeviceReports() {
        val body = PlaybackNegotiator.playbackInfoBody("u1", 0, tv.jetflix.phone.data.CodecSupport(listOf("h264"), listOf("aac"))).toString()
        assertTrue(!body.contains("hevc") && !body.contains("vp9") && !body.contains("eac3"))
    }
}

class ExternalSubtitleTest {
    private fun stream(i: Int, codec: String, method: String, ext: Boolean, url: String? = "/Videos/x/y/Subtitles/$i/0/Stream.$codec") =
        tv.jetflix.phone.data.MediaStreamInfo(Index = i, Type = "Subtitle", Codec = codec, DeliveryMethod = method, DeliveryUrl = url, IsExternal = ext, Language = "eng", DisplayTitle = "English")

    private val src = tv.jetflix.phone.data.MediaSourceInfo(
        Id = "y",
        MediaStreams = listOf(
            stream(2, "srt", "External", false),
            stream(3, "srt", "External", true),
            stream(4, "pgssub", "Drop", false, null),
            stream(5, "ass", "External", false),
        ),
    )

    @org.junit.Test fun directPlayAddsOnlySidecarFiles() {
        val t = tv.jetflix.phone.data.PlaybackNegotiator.externalSubtitles(src, "http://s", directPlay = true)
        org.junit.Assert.assertEquals(listOf("http://s/Videos/x/y/Subtitles/3/0/Stream.srt"), t.map { it.url })
        org.junit.Assert.assertEquals("application/x-subrip", t[0].mimeType)
    }

    @org.junit.Test fun transcodeAddsEveryTextTrackButNotPictureOnes() {
        val t = tv.jetflix.phone.data.PlaybackNegotiator.externalSubtitles(src, "http://s", directPlay = false)
        org.junit.Assert.assertEquals(3, t.size)
        org.junit.Assert.assertTrue(t.any { it.mimeType == "text/x-ssa" })
    }

    @org.junit.Test fun profileOffersTextFormatsOnly() {
        val s = tv.jetflix.phone.data.PlaybackNegotiator.deviceProfile().toString()
        org.junit.Assert.assertTrue(s.contains("\"Format\":\"srt\""))
        org.junit.Assert.assertFalse(s.contains("pgs"))
    }
}

class BroadPlaybackProfileTest {
    private val uhd = tv.jetflix.phone.data.CodecSupport(
        video = listOf("h264", "hevc"), audio = listOf("aac", "ac3", "eac3"),
        hevcMain10 = true, hevcMainLevel = 153, hevcMain10Level = 153, avcLevel = 52,
        maxResolution = mapOf("hevc" to (4096 to 2304), "h264" to (1920 to 1088)), hdr10 = true,
    )

    @org.junit.Test fun offersOfficialClientBitrateNotTheOldCap() {
        val b = tv.jetflix.phone.data.PlaybackNegotiator.playbackInfoBody("u", 0, uhd).toString()
        org.junit.Assert.assertTrue(b.contains("\"MaxStreamingBitrate\":100000000"))
        org.junit.Assert.assertFalse(b.contains("40000000"))
    }

    @org.junit.Test fun sendsDecoderLimitsSoOversizedFilesAreTranscoded() {
        val s = tv.jetflix.phone.data.PlaybackNegotiator.deviceProfile(uhd).toString()
        org.junit.Assert.assertTrue(s.contains("\"Property\":\"VideoLevel\"") && s.contains("\"Value\":\"153\""))
        org.junit.Assert.assertTrue(s.contains("\"Property\":\"Width\"") && s.contains("\"Value\":\"4096\""))
        org.junit.Assert.assertTrue(s.contains("main 10"))
    }

    @org.junit.Test fun hdrFormatsTheTvLacksAreRoutedToTranscode() {
        val bad = tv.jetflix.phone.data.PlaybackNegotiator.unsupportedHevcRanges(uhd)
        org.junit.Assert.assertTrue("DOVI" in bad && "HDR10Plus" in bad)
        org.junit.Assert.assertFalse("HDR10" in bad)
        val sdrOnly = tv.jetflix.phone.data.PlaybackNegotiator.unsupportedHevcRanges(uhd.copy(hdr10 = false))
        org.junit.Assert.assertTrue("HDR10" in sdrOnly)
    }

    @org.junit.Test fun keepsHevcWhenOnlyAudioNeedsConverting() {
        val s = tv.jetflix.phone.data.PlaybackNegotiator.deviceProfile(uhd).toString()
        org.junit.Assert.assertTrue(s.contains("\"VideoCodec\":\"hevc,h264\""))
    }

    @org.junit.Test fun retryAfterDeviceFailureForcesAReEncode() {
        val b = tv.jetflix.phone.data.PlaybackNegotiator.playbackInfoBody("u", 5, uhd, forceTranscode = true).toString()
        org.junit.Assert.assertTrue(b.contains("\"EnableDirectPlay\":false") && b.contains("\"AllowVideoStreamCopy\":false"))
        org.junit.Assert.assertTrue(b.contains("\"VideoCodec\":\"h264\"") && !b.contains("hevc,h264"))
    }

    @org.junit.Test fun unknownDecoderLimitsSendNoLimits() {
        val s = tv.jetflix.phone.data.PlaybackNegotiator.deviceProfile(tv.jetflix.phone.data.CodecSupport.Conservative).toString()
        org.junit.Assert.assertFalse(s.contains("\"Property\":\"Width\"") && s.contains("LessThanEqual\",\"Property\":\"VideoLevel"))
        org.junit.Assert.assertFalse(s.contains("hevc"))
    }
}
