package tv.jetflix

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import org.junit.Assert.*
import org.junit.Test
import tv.jetflix.app.ui.player.TrackCycler
import tv.jetflix.app.ui.player.TrackPrefs

class TrackPrefsTest {
    private fun opt(type: Int) = TrackCycler.Option(type, "x", false, null, -1)
    private val base = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

    @Test fun defaultIsEnglishAudioSubtitlesOff() {
        val p = TrackPrefs().apply(base)
        assertEquals(listOf(androidx.media3.common.util.Util.normalizeLanguageCode("eng")), p.preferredAudioLanguages)
        assertTrue(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }
    @Test fun subtitleLanguageEnablesText() {
        val p = TrackPrefs("eng", "spa").apply(base)
        assertFalse(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals(listOf(androidx.media3.common.util.Util.normalizeLanguageCode("spa")), p.preferredTextLanguages)
    }
    @Test fun offRowSavesOffAndKeepsAudio() {
        val after = TrackPrefs("fra", "eng").after(opt(C.TRACK_TYPE_TEXT)) { null }
        assertTrue(after.subtitlesOff); assertEquals("fra", after.audioLanguage)
    }
    @Test fun audioPickSavesLanguageNotIndex() {
        assertEquals("jpn", TrackPrefs().after(opt(C.TRACK_TYPE_AUDIO)) { "jpn" }.audioLanguage)
    }
    @Test fun unlabeledTrackLeavesPrefsUnchanged() {
        assertEquals(TrackPrefs(), TrackPrefs().after(opt(C.TRACK_TYPE_AUDIO)) { null })
    }
    @Test fun speedRowIgnored() {
        assertEquals(TrackPrefs(), TrackPrefs().after(opt(TrackCycler.TYPE_SPEED)) { "xx" })
    }
}
