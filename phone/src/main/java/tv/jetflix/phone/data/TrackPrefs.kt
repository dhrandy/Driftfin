package tv.jetflix.phone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters

/**
 * Persistent audio and subtitle choices, stored as languages rather than track indexes (indexes differ per title).
 * Defaults: English audio when the title has it, subtitles off. A manual pick in the player saves itself here and
 * applies to the next title and after an app restart. Media3 normalizes language codes (en/eng) and falls back to
 * its own default when a title has no matching track.
 */
object TrackPrefs {
    private const val OFF = "off"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("track_prefs", Context.MODE_PRIVATE)
    }

    /** Saved audio language, or null for the English default. */
    var audioLanguage: String?
        get() = prefs?.getString("audio_language", null)
        set(v) { prefs?.edit()?.putString("audio_language", v)?.apply() }

    /** Saved subtitle choice: a language, "off", or null for the default (also off). */
    var subtitleChoice: String?
        get() = prefs?.getString("subtitle_language", null)
        set(v) { prefs?.edit()?.putString("subtitle_language", v)?.apply() }

    /** The track selection to start a player with: saved choices, or English audio and no subtitles by default. */
    fun applyTo(p: TrackSelectionParameters): TrackSelectionParameters {
        val b = p.buildUpon().setPreferredAudioLanguage(audioLanguage ?: "eng")
        val sub = subtitleChoice
        if (sub == null || sub == OFF) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(sub)
        return b.build()
    }
}
