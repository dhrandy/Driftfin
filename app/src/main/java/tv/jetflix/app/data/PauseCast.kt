package tv.jetflix.app.data

// Pause cast: episode actors first; when the episode carries none (some servers only
// populate People on the series), fall back to the series cast.
internal suspend fun loadPauseCast(
    api: JellyfinApi,
    userId: String,
    item: BaseItemDto
): List<BaseItemPerson> {
    fun actors(dto: BaseItemDto?) = dto?.People.orEmpty().filter { it.Type == "Actor" && it.Id != null }
    val own = runCatching { api.getItem(item.Id, userId).body() }.getOrNull()
    val ownActors = actors(own)
    if (ownActors.isNotEmpty()) return ownActors.take(15)
    val seriesId = item.SeriesId ?: own?.SeriesId ?: return emptyList()
    val seriesActors = runCatching { api.getItem(seriesId, userId).body() }.getOrNull().let(::actors)
    return seriesActors.take(15)
}
