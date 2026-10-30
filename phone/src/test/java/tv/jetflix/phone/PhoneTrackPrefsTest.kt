package tv.jetflix.phone

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.TrackPrefs

/** Language-stored track choices: English audio and subtitles off by default, manual picks persist. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneTrackPrefsTest {
    private fun base(): TrackSelectionParameters = TrackSelectionParameters.Builder(ApplicationProvider.getApplicationContext()).build()

    @Before fun freshStore() {
        TrackPrefs.init(ApplicationProvider.getApplicationContext())
        TrackPrefs.audioLanguage = null
        TrackPrefs.subtitleChoice = null
    }

    @Test fun defaultsAreEnglishAudioAndSubtitlesOff() {
        val p = TrackPrefs.applyTo(base())
        assertEquals("en", p.preferredAudioLanguages.firstOrNull()) // Media3 normalizes ISO-3 eng to ISO-2 en
        assertTrue(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test fun manualAudioPickAppliesByLanguageNotIndex() {
        TrackPrefs.audioLanguage = "jpn"
        assertEquals("ja", TrackPrefs.applyTo(base()).preferredAudioLanguages.firstOrNull()) // jpn normalizes to ja
    }

    @Test fun savedSubtitleLanguageEnablesText() {
        TrackPrefs.subtitleChoice = "spa"
        val p = TrackPrefs.applyTo(base())
        assertFalse(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertTrue(p.preferredTextLanguages.contains("es")) // spa normalizes to es
    }

    @Test fun explicitOffStaysOff() {
        TrackPrefs.subtitleChoice = "off"
        assertTrue(TrackPrefs.applyTo(base()).disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test fun choicesSurviveStoreRecreation() {
        TrackPrefs.audioLanguage = "fra"
        TrackPrefs.subtitleChoice = "por"
        // Re-init reads the same SharedPreferences, the way a process restart would.
        TrackPrefs.init(ApplicationProvider.getApplicationContext())
        assertEquals("fra", TrackPrefs.audioLanguage)
        assertEquals("por", TrackPrefs.subtitleChoice)
    }

    @Test fun nothingSavedReadsAsDefaults() {
        assertNull(TrackPrefs.audioLanguage)
        assertNull(TrackPrefs.subtitleChoice)
    }
}
