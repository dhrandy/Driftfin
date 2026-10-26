package tv.jetflix.app.data

import kotlinx.serialization.Serializable

/** One typed span of a video from Jellyfin's Media Segments API (server 10.10 and newer). Ticks are 100 ns units. */
@Serializable
data class MediaSegmentDto(
    val Id: String? = null,
    val ItemId: String? = null,
    val Type: String? = null,
    val StartTicks: Long = 0,
    val EndTicks: Long = 0,
)

@Serializable
data class MediaSegmentsResponse(val Items: List<MediaSegmentDto> = emptyList())

/** Decides when the "Skip Intro" prompt shows. Uses only segment data the server reports; nothing is guessed. */
object SkipIntro {
    private const val TICKS_PER_MS = 10_000L
    /** Hide the prompt when almost nothing is left to skip. */
    private const val MIN_REMAINING_MS = 1_000L

    /** The Intro segment the playhead is inside right now, or null. */
    fun active(segments: List<MediaSegmentDto>, positionMs: Long): MediaSegmentDto? =
        segments.firstOrNull { s ->
            s.Type.equals("Intro", ignoreCase = true) &&
                s.EndTicks > s.StartTicks &&
                positionMs >= s.StartTicks / TICKS_PER_MS &&
                positionMs < s.EndTicks / TICKS_PER_MS - MIN_REMAINING_MS
        }

    fun endMs(segment: MediaSegmentDto): Long = segment.EndTicks / TICKS_PER_MS
}
