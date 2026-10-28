package tv.jetflix

import org.junit.Assert.*
import org.junit.Test
import tv.jetflix.app.data.*

/** Synthetic server metadata: two audio tracks, Japanese first. */
class HlsAudioPreferenceTest {
    private fun src(direct: Boolean, transcodeAudio: Int?, default: Int? = 1) = MediaSourceInfo(
        Id = "s1", SupportsDirectPlay = direct, SupportsDirectStream = direct, SupportsTranscoding = true,
        TranscodingUrl = if (!direct) "/videos/x/master.m3u8?MediaSourceId=s1" + (transcodeAudio?.let { "&AudioStreamIndex=$it" } ?: "") else null,
        DefaultAudioStreamIndex = default,
        MediaStreams = listOf(
            MediaStreamInfo(Index = 0, Type = "Video"),
            MediaStreamInfo(Index = 1, Type = "Audio", Language = "jpn"),
            MediaStreamInfo(Index = 2, Type = "Audio", Language = "eng"),
        ),
    )
    private fun info(s: MediaSourceInfo) = PlaybackInfoResponse(listOf(s), "p")

    @Test fun transcodeWithJapaneseDefaultAsksForEnglishIndex() =
        assertEquals("s1" to 2, PlaybackNegotiator.audioRetry(info(src(false, null, default = 1)), "eng"))
    @Test fun twoLetterLanguageMatchesToo() =
        assertEquals("s1" to 2, PlaybackNegotiator.audioRetry(info(src(false, null)), "en"))
    @Test fun alreadyEnglishAsksNothing() = assertNull(PlaybackNegotiator.audioRetry(info(src(false, 2)), "eng"))
    @Test fun urlAudioIndexBeatsDefault() = assertNull(PlaybackNegotiator.audioRetry(info(src(false, 2, default = 1)), "eng"))
    @Test fun directPlayKeepsAllTracksNoRetry() = assertNull(PlaybackNegotiator.audioRetry(info(src(true, null)), "eng"))
    @Test fun noEnglishTrackNoRetry() = assertNull(PlaybackNegotiator.audioRetry(info(src(false, null)), "fra"))
    @Test fun secondRequestCarriesIndexSourceAndNoSubtitles() {
        val b = PlaybackNegotiator.playbackInfoBody("u", 0, audioStreamIndex = 2, mediaSourceId = "s1", subtitlesOff = true).toString()
        assertTrue(b, b.contains("\"AudioStreamIndex\":2") && b.contains("\"MediaSourceId\":\"s1\"") && b.contains("\"SubtitleStreamIndex\":-1"))
    }
    @Test fun subtitlesOnLeavesIndexOut() {
        assertFalse(PlaybackNegotiator.playbackInfoBody("u", 0, subtitlesOff = false).toString().contains("SubtitleStreamIndex"))
    }
}
