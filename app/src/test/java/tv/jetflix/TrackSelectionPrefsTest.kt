package tv.jetflix

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.test.utils.FakeRenderer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.ui.player.TrackCycler
import tv.jetflix.app.ui.player.TrackPrefs

/** Real Media3 track selection with synthetic tracks, driven by the saved preferences. */
@RunWith(RobolectricTestRunner::class)
class TrackSelectionPrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun audio(lang: String?) = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(lang).build())
    private fun text(lang: String?, flags: Int = 0) =
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage(lang).setSelectionFlags(flags).build())

    /** Returns (selected audio language, selected text language or "off"). */
    private fun select(prefs: TrackPrefs, vararg groups: TrackGroup): Pair<String?, String?> {
        val selector = DefaultTrackSelector(ctx)
        selector.init({}, androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(ctx))
        selector.parameters = prefs.apply(selector.parameters) as androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters
        val renderers = arrayOf(FakeRenderer(C.TRACK_TYPE_AUDIO), FakeRenderer(C.TRACK_TYPE_TEXT))
        val caps = renderers.map { it.getCapabilities() }.toTypedArray<RendererCapabilities>()
        val arr = TrackGroupArray(*groups)
        val r = selector.selectTracks(caps, arr, MediaSource.MediaPeriodId(Any()), Timeline.EMPTY)
        fun lang(i: Int): String? {
            val sel = r.selections[i] ?: return null
            return sel.getFormat(0).language ?: "und"
        }
        return lang(0) to lang(1)
    }

    @Test fun englishAudioPreferredOverFirstTrackAndSubtitlesOff() {
        val (a, t) = select(TrackPrefs(), audio("jpn"), audio("eng"), text("eng"), text("eng", C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED))
        assertEquals("eng", a?.take(3)?.let { if (it == "en") "eng" else it } ?: a)
        assertNull("subtitles off even with default/forced track", t)
    }
    @Test fun twoLetterAndThreeLetterEnglishBothMatch() {
        val (a, _) = select(TrackPrefs("eng"), audio("fra"), audio("en"))
        assertTrue(a == "en" || a == "eng")
        val (b, _) = select(TrackPrefs("eng"), audio("fra"), audio("eng"))
        assertTrue(b == "en" || b == "eng")
    }
    @Test fun noEnglishFallsBackToAFirstPlayableTrack() {
        val (a, _) = select(TrackPrefs(), audio("jpn"), audio("fra"))
        assertEquals(androidx.media3.common.util.Util.normalizeLanguageCode("jpn"), a)
    }
    @Test fun savedSubtitleLanguageIsSelected() {
        val (_, t) = select(TrackPrefs("eng", "spa"), audio("eng"), text("eng"), text("spa"))
        assertTrue(t == "es" || t == "spa")
    }
    @Test fun manualOverridePersistsAcrossStoreRecreationAndNewItem() {
        AppSettings.init(ctx)
        AppSettings.audioLanguage = "eng"; AppSettings.subtitleLanguage = null
        val pick = TrackCycler.Option(C.TRACK_TYPE_AUDIO, "x", false, null, -1)
        val saved = TrackPrefs(AppSettings.audioLanguage, AppSettings.subtitleLanguage).after(pick) { "jpn" }
        AppSettings.audioLanguage = saved.audioLanguage; AppSettings.subtitleLanguage = saved.subtitleLanguage
        // "restart": a fresh init reads the same file
        AppSettings.init(ctx)
        assertEquals("jpn", AppSettings.audioLanguage)
        assertNull(AppSettings.subtitleLanguage)
        // a different title with different track order gets the language, not an index
        val (a, _) = select(TrackPrefs(AppSettings.audioLanguage, AppSettings.subtitleLanguage), audio("eng"), audio("fra"), audio("jpn"))
        assertEquals(androidx.media3.common.util.Util.normalizeLanguageCode("jpn"), a)
    }
    @Test fun defaultsWhenNothingSaved() {
        ctx.getSharedPreferences("jetflix_settings", Context.MODE_PRIVATE).edit().clear().commit()
        AppSettings.init(ctx)
        assertEquals("eng", AppSettings.audioLanguage); assertNull(AppSettings.subtitleLanguage)
    }
}
