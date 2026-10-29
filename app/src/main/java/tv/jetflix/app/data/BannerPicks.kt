package tv.jetflix.app.data

/** Which titles may appear in the Home, Movies and Shows banners. Watched titles and episodes are never used. */
object BannerPicks {
    const val MIN_RATING = 7.0f

    private val BaseItemDto.untouched get() =
        UserData?.Played != true && (UserData?.PlaybackPositionTicks ?: 0L) == 0L && UserData?.LastPlayedDate == null

    /** Unwatched movies, best CommunityRating first. Titles rated MIN_RATING or more win; otherwise the best-rated unwatched ones. */
    fun topUnwatchedMovies(items: List<BaseItemDto>, hasBackdrop: (BaseItemDto) -> Boolean, count: Int): List<BaseItemDto> =
        topUnwatchedMovies(items, hasBackdrop, count, startedOk = false)

    /**
     * [startedOk] relaxes the untouched gate to anything the server counts as unwatched (Played is not true),
     * so started-but-unfinished titles are eligible too. Used only as a fallback when the strict gate leaves
     * nothing: watched titles are still never used.
     */
    fun topUnwatchedMovies(items: List<BaseItemDto>, hasBackdrop: (BaseItemDto) -> Boolean, count: Int, startedOk: Boolean): List<BaseItemDto> {
        val pool = items.filter { it.isMovie && (if (startedOk) it.UserData?.Played != true else it.untouched) && hasBackdrop(it) }.sortedByDescending { it.CommunityRating ?: 0f }
        val high = pool.filter { (it.CommunityRating ?: 0f) >= MIN_RATING }
        return (if (high.isNotEmpty()) high else pool).take(count)
    }

    const val HOME_POOL = 50

    /**
     * Home banner: the same eligible titles as [topUnwatchedMovies], limited to the best [pool], then [count] of them
     * chosen at random. Called once per Home load, so each visit to a freshly loaded Home shows a new set.
     *
     * The banner must not vanish just because the strict gates emptied the pool (Todd's row-82/86 repro: a
     * successful load plus the exclude-animation option plus the untouched gate left zero titles, and the empty
     * result replaced a good banner). When nothing untouched is eligible, retry once with started-but-unfinished
     * titles allowed. The animation exclusion and the backdrop requirement still apply, and watched titles are
     * still never used; if even the relaxed pool is empty there is genuinely nothing to show.
     */
    fun randomTopUnwatchedMovies(items: List<BaseItemDto>, hasBackdrop: (BaseItemDto) -> Boolean, count: Int, pool: Int = HOME_POOL, random: kotlin.random.Random = kotlin.random.Random, excludeAnimation: Boolean = false): List<BaseItemDto> {
        val filtered = if (excludeAnimation) items.filterNot { item -> item.Genres.orEmpty().any { it.trim().equals("Animation", ignoreCase = true) } } else items
        val strict = topUnwatchedMovies(filtered, hasBackdrop, pool)
        val eligible = if (strict.isEmpty()) topUnwatchedMovies(filtered, hasBackdrop, pool, startedOk = true) else strict
        return eligible.shuffled(random).take(count)
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
