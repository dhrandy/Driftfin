package tv.jetflix.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters

/**
 * Remembered audio and subtitle choice, kept as languages (never track indexes, which differ per title).
 * Defaults: English audio, subtitles off. A manual choice in the player panel replaces the default.
 */
data class TrackPrefs(val audioLanguage: String = "eng", val subtitleLanguage: String? = null) {
    /** subtitleLanguage == null means subtitles off. */
    val subtitlesOff: Boolean get() = subtitleLanguage == null

    fun apply(base: TrackSelectionParameters): TrackSelectionParameters {
        val b = base.buildUpon().setPreferredAudioLanguage(audioLanguage)
        if (subtitleLanguage == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).setPreferredTextLanguage(null)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(subtitleLanguage)
        }
        return b.build()
    }

    /** The prefs after the viewer picks [option]; unchanged for speed rows or tracks with no language. */
    fun after(option: TrackCycler.Option, languageOf: (TrackCycler.Option) -> String?): TrackPrefs = when {
        option.type == C.TRACK_TYPE_AUDIO -> languageOf(option)?.let { copy(audioLanguage = it) } ?: this
        option.type == C.TRACK_TYPE_TEXT && option.group == null -> copy(subtitleLanguage = null)
        option.type == C.TRACK_TYPE_TEXT -> languageOf(option)?.let { copy(subtitleLanguage = it) } ?: this
        else -> this
    }
}
