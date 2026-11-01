package tv.jetflix.app.data

// Pause cast for the episode being watched. The user asked for exactly the people in THAT
// episode, guests included, and never another season's or the series' cast.
//
// For an episode: local credits are the server's OWN People on that episode, Actors and
// GuestStars together. Local credits are never treated as "complete": when a TVMaze season is
// verified episode-by-episode for this exact season (see TvmazeCastResolver), the verified
// episode's guest cast is merged in as enrichment (local entries first, keeping their Jellyfin
// ids so OK still opens their pages; TVMaze-only people are display-only). When no season
// verifies, local credits are shown as-is. When the episode has no local credits and no
// verified TVMaze match, the cast row stays EMPTY - a wrong-season list is worse than none.
// TVMaze's show-level regulars are never fetched: they are not this episode's credits.
//
// The legacy series-level fallback below now only applies to non-episode items.
internal suspend fun loadPauseCast(
    api: JellyfinApi,
    userId: String,
    item: BaseItemDto,
    tvmaze: TvmazeCastResolver? = null,
): List<BaseItemPerson> {
    fun credited(dto: BaseItemDto?) = dto?.People.orEmpty().filter { (it.Type == "Actor" || it.Type == "GuestStar") && it.Id != null }
    val own = runCatching { api.getItem(item.Id, userId).body() }.getOrNull()
    if (item.isEpisode) {
        val local = credited(own)
        val seriesId = item.SeriesId ?: own?.SeriesId
        if (tvmaze != null && seriesId != null && item.SeasonId != null) {
            val seasonEpisodes = runCatching {
                api.getEpisodes(seriesId, userId, seasonId = item.SeasonId, fields = "PremiereDate").body()?.Items
            }.getOrNull().orEmpty()
            val guestCast = runCatching { tvmaze.episodeCast(item, seasonEpisodes) }.getOrNull()
            if (!guestCast.isNullOrEmpty()) {
                // Local credits stay untouched, first, with their Jellyfin ids (OK opens their
                // pages). A TVMaze person is dropped as a duplicate ONLY when a local person's
                // provider ids prove it is the same person (local ProviderIds["Tvmaze"] == the
                // TVMaze person id - happens when the server itself used a TVMaze agent).
                // A name match alone is not proof - homonyms exist - so unverifiable
                // look-alikes are both kept rather than silently collapsed.
                val localTvmazeIds = local.mapNotNull { it.ProviderIds?.get("Tvmaze") }.toSet()
                val merged = local + guestCast.filter { g ->
                    if (g.ProviderIds?.get("Tvmaze") in localTvmazeIds) return@filter false
                    // Display dedup, only inside this one verified episode: hide the TVMaze guest
                    // card when a local credit in the SAME episode carries the same normalized name
                    // AND the same normalized character (both non-blank). The local card wins - it
                    // keeps its Jellyfin id, so OK still opens the person's page. Nothing is merged
                    // or rewritten. When the name matches but the character differs or is blank on
                    // either side, the match is ambiguous and BOTH cards stay: a look-alike is never
                    // silently deleted. (TVMaze exposes no external person ids to do better.)
                    val gRole = TvmazeCastResolver.normalizeTitle(g.Role)
                    if (gRole.isEmpty()) return@filter true
                    val gName = TvmazeCastResolver.normalizeTitle(g.Name)
                    local.none { l ->
                        TvmazeCastResolver.normalizeTitle(l.Name) == gName && TvmazeCastResolver.normalizeTitle(l.Role) == gRole
                    }
                }
                if (merged.isNotEmpty()) return merged.take(15)
            }
        }
        return local.take(15)
    }
    val ownCredited = credited(own)
    if (ownCredited.isNotEmpty()) return ownCredited.take(15)
    val seriesId = item.SeriesId ?: own?.SeriesId ?: return emptyList()
    return credited(runCatching { api.getItem(seriesId, userId).body() }.getOrNull()).take(15)
}
