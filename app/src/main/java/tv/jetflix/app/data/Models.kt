package tv.jetflix.app.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticateUserByName(
    val Username: String,
    val Pw: String,
)

@Serializable
data class AuthenticationResult(
    val User: UserDto? = null,
    val SessionInfo: SessionInfoDto? = null,
    val AccessToken: String? = null,
    val ServerId: String? = null,
)

@Serializable
data class SessionInfoDto(val Id: String? = null)

@Serializable
data class UserDto(
    val Name: String? = null,
    val ServerId: String? = null,
    val Id: String = "",
)

@Serializable
data class PublicSystemInfo(
    val LocalAddress: String? = null,
    val ServerName: String? = null,
    val Version: String? = null,
    val ProductName: String? = null,
    val Id: String? = null,
    val StartupWizardCompleted: Boolean? = null,
)

@Serializable
data class BaseItemDtoQueryResult(
    val Items: List<BaseItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
    val StartIndex: Int = 0,
)

@Serializable
data class UserItemDataDto(
    val PlaybackPositionTicks: Long = 0,
    val PlayedPercentage: Double? = null,
    val Played: Boolean = false,
    val UnplayedItemCount: Int? = null,
    val LastPlayedDate: String? = null,
    val IsFavorite: Boolean = false,
)

@Serializable
data class NameGuidPair(val Name: String? = null, val Id: String? = null)

@Serializable
data class BaseItemPerson(
    val Name: String? = null,
    val Id: String? = null,
    val Role: String? = null,
    val Type: String? = null,
    val PrimaryImageTag: String? = null,
)

@Serializable
data class MediaSourceInfo(
    val Id: String? = null,
    val Container: String? = null,
    val Size: Long? = null,
    val SupportsDirectPlay: Boolean? = null,
    val SupportsDirectStream: Boolean? = null,
    val SupportsTranscoding: Boolean? = null,
    val TranscodingUrl: String? = null,
    val TranscodingSubProtocol: String? = null,
    val DefaultAudioStreamIndex: Int? = null,
    val MediaStreams: List<MediaStreamInfo> = emptyList(),
)

@Serializable
data class MediaStreamInfo(
    val Index: Int? = null,
    val Type: String? = null,
    val Codec: String? = null,
    val DeliveryMethod: String? = null,
    val DeliveryUrl: String? = null,
    val IsExternal: Boolean? = null,
    val Language: String? = null,
    val DisplayTitle: String? = null,
    val Width: Int? = null,
    val Height: Int? = null,
)

@Serializable
data class PlaybackInfoResponse(
    val MediaSources: List<MediaSourceInfo> = emptyList(),
    val PlaySessionId: String? = null,
)

@Serializable
data class BaseItemDto(
    val Id: String = "",
    val Name: String? = null,
    val OriginalTitle: String? = null,
    val Type: String? = null,
    val MediaType: String? = null,
    val CollectionType: String? = null,
    val IsFolder: Boolean? = null,
    val Overview: String? = null,
    val ProductionYear: Int? = null,
    val PremiereDate: String? = null,
    val EndDate: String? = null,
    val CommunityRating: Float? = null,
    val OfficialRating: String? = null,
    val RunTimeTicks: Long? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val SeriesPrimaryImageTag: String? = null,
    val SeasonId: String? = null,
    val SeasonName: String? = null,
    val ParentIndexNumber: Int? = null,
    val IndexNumber: Int? = null,
    val ChildCount: Int? = null,
    val RecursiveItemCount: Int? = null,
    val Genres: List<String> = emptyList(),
    val GenreItems: List<NameGuidPair> = emptyList(),
    val Studios: List<NameGuidPair> = emptyList(),
    val People: List<BaseItemPerson> = emptyList(),
    val ImageTags: Map<String, String> = emptyMap(),
    val BackdropImageTags: List<String> = emptyList(),
    val ParentBackdropItemId: String? = null,
    val ParentBackdropImageTags: List<String> = emptyList(),
    val ParentThumbItemId: String? = null,
    val ParentThumbImageTag: String? = null,
    val SeriesStatus: String? = null,
    val UserData: UserItemDataDto? = null,
    val RemoteTrailers: List<MediaUrl> = emptyList(),
    val LocalTrailerCount: Int? = null,
    val MediaSources: List<MediaSourceInfo> = emptyList(),
) {
    val isMovie get() = Type == "Movie"
    val isSeries get() = Type == "Series"
    val isEpisode get() = Type == "Episode"
    val isResumeable get() = (UserData?.PlaybackPositionTicks ?: 0) > 0

    fun displayTitle(): String = when {
        isEpisode && SeriesName != null -> SeriesName
        else -> Name ?: "Untitled"
    }

    fun episodeLabel(): String? {
        if (!isEpisode) return null
        val s = ParentIndexNumber ?: return null
        val e = IndexNumber ?: return null
        return "S${s}:E${e}"
    }

    fun runtimeLabel(): String? {
        val ticks = RunTimeTicks ?: return null
        val totalMinutes = ticks / 600_000_000
        if (totalMinutes <= 0) return null
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

/** Same item, but playback starts at 0:00 instead of the saved position. */
fun BaseItemDto.fromStart(): BaseItemDto = copy(UserData = UserData?.copy(PlaybackPositionTicks = 0))

fun Long.ticksToHms(): String {
    var total = this / 10_000_000
    val h = total / 3600
    total %= 3600
    val m = total / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun Long.ticksToMs(): Long = this / 10_000L
fun Long.msToTicks(): Long = this * 10_000L

@Serializable
data class PublicUser(val Name: String? = null, val Id: String? = null)

/** Picks the episode that follows [currentId] in a series episode list (server order). */
object NextEpisode {
    fun pick(episodes: List<BaseItemDto>, currentId: String): BaseItemDto? {
        val i = episodes.indexOfFirst { it.Id == currentId }
        return if (i >= 0) episodes.getOrNull(i + 1) else null
    }
}

/** Which episode a series' Play/Resume button should start. Server order = season, then episode. */
object EpisodeTarget {
    fun pick(episodes: List<BaseItemDto>): BaseItemDto? {
        if (episodes.isEmpty()) return null
        val inProgress = episodes.withIndex().filter { (_, e) ->
            e.UserData?.Played != true && (e.UserData?.PlaybackPositionTicks ?: 0) > 0
        }
        if (inProgress.isNotEmpty()) {
            return inProgress.maxWith(
                compareBy<IndexedValue<BaseItemDto>>({ it.value.UserData?.LastPlayedDate ?: "" }, { it.index }),
            ).value
        }
        val lastWatched = episodes.withIndex().filter { it.value.UserData?.Played == true }
            .maxWithOrNull(compareBy<IndexedValue<BaseItemDto>>({ it.value.UserData?.LastPlayedDate ?: "" }, { it.index }))
        if (lastWatched != null) {
            episodes.getOrNull(lastWatched.index + 1)?.let { return it }
        }
        return episodes.firstOrNull { it.UserData?.Played != true } ?: episodes.first()
    }
}

/**
 * Whether a title offers a 4K version, read from the server's own video stream sizes (never from the title or artwork).
 * "4K" uses the same cut-off as Jellyfin's web client: width of 3800 or more, or height of 2000 or more.
 * This says a 4K version exists; it does not promise which version plays.
 */
object Resolution4K {
    /** True when any of the item's media sources has a 4K video stream; false when none does; null when no source gave a size. */
    fun of(item: BaseItemDto): Boolean? {
        var known = false
        for (source in item.MediaSources) {
            val video = source.MediaStreams?.firstOrNull { it.Type == "Video" } ?: continue
            val w = video.Width ?: 0
            val h = video.Height ?: 0
            if (w <= 0 && h <= 0) continue
            known = true
            if (w >= 3800 || h >= 2000) return true
        }
        return if (known) false else null
    }

    /** Movie badge: shown when any of the movie's own versions is 4K. Episodes use the server's is4K filter instead; shows get none. */
    fun badge(item: BaseItemDto): Boolean = item.Type == "Movie" && of(item) == true
}
