package tv.jetflix.phone.ui

import tv.jetflix.phone.data.BaseItemDto
import tv.jetflix.phone.data.JellyfinApi
import tv.jetflix.phone.data.NextEpisode
import tv.jetflix.phone.data.PlaybackStopInfo

/** Result of looking up the episode after the one that just ended. */
internal sealed interface UpNextLookup {
    data class Found(val next: BaseItemDto) : UpNextLookup
    data object None : UpNextLookup
    data object Failed : UpNextLookup
}

/** Server-order next episode, distinguishing "none left" from "the lookup itself failed". */
internal suspend fun lookupUpNext(api: JellyfinApi, seriesId: String, userId: String, currentId: String): UpNextLookup {
    val resp = try {
        api.getEpisodes(seriesId, userId)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return UpNextLookup.Failed }
    if (!resp.isSuccessful) return UpNextLookup.Failed
    val next = NextEpisode.pick(resp.body()?.Items ?: emptyList(), currentId)
    return if (next != null) UpNextLookup.Found(next) else UpNextLookup.None
}

/** Once-only guard for a play session's final stop report. The natural-finish paths send their own
 *  awaited stop + watched mark and then arm this, so the fire-and-forget dispose report skips:
 *  a second stop landing after the watched mark can make the server clear the mark and resurrect a
 *  resume point. The tradeoff is deliberate: when the finish path's stop failed, the dispose report
 *  no longer retries it, because that retry is exactly what can arrive after the mark. */
internal class StopReportOnce {
    private val sent = java.util.concurrent.atomic.AtomicBoolean(false)

    /** True for the one caller that may send the stop report; false for everyone after. */
    fun claim(): Boolean = !sent.getAndSet(true)
}

/** Only movies and episodes earn the watched check at their natural end. */
internal fun earnsWatchedAtEnd(item: BaseItemDto): Boolean = item.isMovie || item.isEpisode

/**
 * Absolute content position for a server report. A resumed HLS transcode starts encoding at the
 * resume offset and ExoPlayer counts that stream from zero, so its position is relative to the
 * offset; direct play seeks inside the real timeline and is already absolute (offset 0). Without
 * the base, every progress/stop report collapses toward the episode start and Jellyfin saves that
 * as the resume point.
 */
internal fun absoluteTicks(streamPositionMs: Long, streamStartTicks: Long): Long =
    streamPositionMs.coerceAtLeast(0L) * 10_000 + streamStartTicks

/** Longest a natural end waits on one server write before giving up and moving on - a hung
 *  connection must never strand the viewer on the end screen. */
internal const val FINISH_WRITE_TIMEOUT_MS = 5_000L

/** The two writes a natural finish needs: the stop report (clears the resume point) and the watched
 *  mark. Both are best-effort and individually time-bounded; a failure leaves the server as it is. */
internal suspend fun reportStopAndMarkPlayed(
    api: JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
    once: StopReportOnce? = null,
) {
    once?.claim()
    try {
        kotlinx.coroutines.withTimeoutOrNull(FINISH_WRITE_TIMEOUT_MS) {
            api.reportPlaybackStopped(
                PlaybackStopInfo(
                    ItemId = item.Id,
                    MediaSourceId = mediaSourceId,
                    PlaySessionId = playSessionId,
                    PositionTicks = positionTicks,
                ),
            )
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    if (earnsWatchedAtEnd(item)) {
        try {
            kotlinx.coroutines.withTimeoutOrNull(FINISH_WRITE_TIMEOUT_MS) {
                api.markPlayed(item.Id, userId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    }
}

/** Natural-end completion that leaves the player: WAIT for the stop report and the watched mark before
 *  navigating, so the page it lands on already shows the title as played - the fire-and-forget dispose
 *  report alone races that fetch. A failed report or mark still navigates. The dispose report fires
 *  again afterwards with the same final position, which is harmless. */
internal suspend fun reportFinishThenNavigate(
    api: JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
    once: StopReportOnce? = null,
    navigate: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks, once)
    navigate()
}

/** Autoplay-off completion path: report stopped and mark watched, WAITing for the server before opening
 *  the next episode's page, so the fresh page's fetch already carries the finished episode's check.
 *  A failed report still navigates. */
internal suspend fun reportStopThenOpenNext(
    api: JellyfinApi,
    item: BaseItemDto,
    userId: String,
    mediaSourceId: String?,
    playSessionId: String?,
    positionTicks: Long,
    once: StopReportOnce? = null,
    onOpenNext: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks, once)
    onOpenNext()
}
