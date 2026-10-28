package tv.jetflix.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

/** Cycles audio or subtitle tracks from the remote. Subtitles include an "Off" step. */
object TrackCycler {

    /** Positions are 0 until count, plus -1 meaning "off" when [allowOff]. Returns the next one. */
    fun nextPosition(current: Int, count: Int, allowOff: Boolean): Int {
        if (count <= 0) return if (allowOff) -1 else 0
        val next = current + 1
        return when {
            next < count -> next
            allowOff -> -1
            else -> 0
        }
    }

    private data class Entry(val group: androidx.media3.common.Tracks.Group, val index: Int, val label: String)

    private fun entries(player: Player, type: Int): List<Entry> =
        player.currentTracks.groups
            .filter { it.type == type }
            .flatMap { g ->
                (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                    val f = g.getTrackFormat(i)
                    val lang = f.language?.let { l -> java.util.Locale.forLanguageTag(l).displayLanguage.ifBlank { l } } ?: "Track"
                    val codec = f.sampleMimeType?.substringAfter('/')?.uppercase()
                    Entry(g, i, listOfNotNull(f.label ?: lang, codec.takeIf { type == C.TRACK_TYPE_AUDIO }).joinToString(" · "))
                }
            }

    /** Moves to the next track of [type] and returns a short on-screen message. */
    fun cycle(player: Player, type: Int): String {
        val name = if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles"
        val all = entries(player, type)
        val allowOff = type == C.TRACK_TYPE_TEXT
        if (all.isEmpty()) return "$name: none available"
        val current = all.indexOfFirst { it.group.isTrackSelected(it.index) }
        val pos = nextPosition(current, all.size, allowOff)
        val params = player.trackSelectionParameters.buildUpon()
        if (pos == -1) {
            params.setTrackTypeDisabled(type, true)
            player.trackSelectionParameters = params.build()
            return "$name: Off"
        }
        val e = all[pos]
        params.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(e.group.mediaTrackGroup, e.index))
        player.trackSelectionParameters = params.build()
        return "$name: ${e.label}"
    }

    /** One selectable row in the audio/subtitle panel. [group] and [index] are null for "Off". */
    data class Option(
        val type: Int,
        val label: String,
        val selected: Boolean,
        internal val group: androidx.media3.common.Tracks.Group?,
        internal val index: Int,
    )

    /** Audio tracks, then subtitle tracks with an explicit Off row; selection state included. */
    const val TYPE_SPEED = -2
    private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    fun options(player: Player): List<Option> {
        val out = mutableListOf<Option>()
        val audio = entries(player, C.TRACK_TYPE_AUDIO)
        audio.forEach { out += Option(C.TRACK_TYPE_AUDIO, it.label, it.group.isTrackSelected(it.index), it.group, it.index) }
        val text = entries(player, C.TRACK_TYPE_TEXT)
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
            text.none { it.group.isTrackSelected(it.index) }
        out += Option(C.TRACK_TYPE_TEXT, "Off", textOff, null, -1)
        text.forEach { out += Option(C.TRACK_TYPE_TEXT, it.label, !textOff && it.group.isTrackSelected(it.index), it.group, it.index) }
        SPEEDS.forEach { s ->
            out += Option(TYPE_SPEED, "${s}x" + if (s == 1.0f) " (normal)" else "", player.playbackParameters.speed == s, null, (s * 100).toInt())
        }
        return out
    }

    /** Language of the track behind [option], or null for Off/speed rows or an unlabeled track. */
    fun languageOf(option: Option): String? =
        option.group?.takeIf { option.index in 0 until it.length }?.getTrackFormat(option.index)?.language
            ?.takeIf { it.isNotBlank() && it != "und" }

    fun apply(player: Player, option: Option) {
        if (option.type == TYPE_SPEED) { player.setPlaybackSpeed(option.index / 100f); return }
        val params = player.trackSelectionParameters.buildUpon()
        if (option.group == null) {
            params.setTrackTypeDisabled(option.type, true)
        } else {
            params.setTrackTypeDisabled(option.type, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
        player.trackSelectionParameters = params.build()
    }
}
