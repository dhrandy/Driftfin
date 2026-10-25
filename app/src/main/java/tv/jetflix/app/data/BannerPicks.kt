package tv.jetflix.app.data

/** Which titles may appear in the Home, Movies and Shows banners. Watched titles and episodes are never used. */
object BannerPicks {
    const val MIN_RATING = 7.0f

    private val BaseItemDto.untouched get() =
        UserData?.Played != true && (UserData?.PlaybackPositionTicks ?: 0L) == 0L && UserData?.LastPlayedDate == null

    /** Unwatched movies, best CommunityRating first. Titles rated MIN_RATING or more win; otherwise the best-rated unwatched ones. */
    fun topUnwatchedMovies(items: List<BaseItemDto>, hasBackdrop: (BaseItemDto) -> Boolean, count: Int): List<BaseItemDto> {
        val pool = items.filter { it.isMovie && it.untouched && hasBackdrop(it) }.sortedByDescending { it.CommunityRating ?: 0f }
        val high = pool.filter { (it.CommunityRating ?: 0f) >= MIN_RATING }
        return (if (high.isNotEmpty()) high else pool).take(count)
    }

    /**
     * Movies and Shows banners: one random title nobody has started. A show counts as unwatched only when Jellyfin
     * reports no played state, no resume position and no last-played date for it; there is no fallback to watched titles.
     */
    fun randomUnwatched(items: List<BaseItemDto>, isShows: Boolean, hasBackdrop: (BaseItemDto) -> Boolean, random: kotlin.random.Random = kotlin.random.Random): BaseItemDto? =
        items.filter { (if (isShows) it.isSeries else it.isMovie) && it.untouched && hasBackdrop(it) }.randomOrNull(random)

    /** Screensaver pool: every untouched movie that has backdrop art, in the order given. Watched titles are never used. */
    fun randomUnwatchedList(items: List<BaseItemDto>, hasBackdrop: (BaseItemDto) -> Boolean): List<BaseItemDto> =
        items.filter { it.isMovie && it.untouched && hasBackdrop(it) }
}
