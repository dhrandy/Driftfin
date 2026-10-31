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

/** Only movies and episodes earn the watched check at their natural end. */
internal fun earnsWatchedAtEnd(item: BaseItemDto): Boolean = item.isMovie || item.isEpisode

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
) {
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
    navigate: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks)
    navigate()
}

/** Watched mark for the finish paths that keep playing (the autoplay countdown) or surface an error:
 *  fire-and-forget on a daemon thread, like the dispose stop report. */
internal fun apiMarkPlayed(api: JellyfinApi, item: BaseItemDto, userId: String) {
    if (!earnsWatchedAtEnd(item)) return
    Thread {
        try {
            kotlinx.coroutines.runBlocking { api.markPlayed(item.Id, userId) }
        } catch (_: Exception) { }
    }.start()
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
    onOpenNext: () -> Unit,
) {
    reportStopAndMarkPlayed(api, item, userId, mediaSourceId, playSessionId, positionTicks)
    onOpenNext()
}
