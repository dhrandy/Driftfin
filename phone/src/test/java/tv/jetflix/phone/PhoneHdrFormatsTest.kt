package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.phone.data.*

class PhoneHdrFormatsTest {
    private fun st(r: String?, t: String?) = MediaStreamInfo(Type = "Video", VideoRange = r, VideoRangeType = t)
    private fun mv(vararg v: MediaStreamInfo, kind: String = "Movie") = BaseItemDto(Id = "x", Type = kind, MediaSources = v.map { MediaSourceInfo(MediaStreams = listOf(MediaStreamInfo(Type = "Audio"), it)) })
    private val none = emptyList<String>()
    @Test fun sdrMissing() { assertEquals(none, HdrFormats.of(mv(st("SDR","SDR")))); assertEquals(none, HdrFormats.of(mv(st(null,null)))); assertEquals(none, HdrFormats.of(BaseItemDto(Id="x", Type="Movie"))) }
    @Test fun plain() {
        assertEquals(listOf("HDR10"), HdrFormats.of(mv(st("HDR","HDR10")))); assertEquals(listOf("HDR10+"), HdrFormats.of(mv(st("HDR","HDR10Plus"))))
        assertEquals(listOf("HLG"), HdrFormats.of(mv(st("HDR","HLG")))); assertEquals(listOf("HDR"), HdrFormats.of(mv(st("HDR",null))))
    }
    @Test fun dv() {
        assertEquals(listOf("Dolby Vision"), HdrFormats.of(mv(st("HDR","DOVI")))); assertEquals(listOf("Dolby Vision","HDR10"), HdrFormats.of(mv(st("HDR","DOVIWithHDR10"))))
        assertEquals(listOf("Dolby Vision","HDR10+"), HdrFormats.of(mv(st("HDR","DOVIWithHDR10Plus"))))
    }
    @Test fun invalid() {
        assertEquals(listOf("HDR"), HdrFormats.of(mv(st("HDR","DOVIInvalid")))); assertEquals(none, HdrFormats.of(mv(st("SDR","DOVIInvalid")))); assertEquals(none, HdrFormats.of(mv(st(null,"DOVIInvalid"))))
    }
    @Test fun versionsAndTypes() {
        assertEquals(listOf("Dolby Vision","HDR10"), HdrFormats.of(mv(st("SDR","SDR"), st("HDR","HDR10"), st("HDR","DOVI"))))
        assertEquals(none, HdrFormats.of(mv(st("HDR","HDR10"), kind = "Episode")))
    }
}
