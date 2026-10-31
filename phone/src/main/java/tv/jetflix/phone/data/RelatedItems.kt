package tv.jetflix.phone.data

/**
 * Jellyfin's similar-items endpoint is loose (it can match on rating or year alone), so a row labelled
 * "More like X" could show unrelated titles. Keep only items of the same kind that share a genre with X,
 * and hide the row if too few are left. When X has no genres we can't vouch for any match, so the row is hidden.
 * Same rule as the TV app.
 */
object RelatedItems {
    const val MIN_ITEMS = 4

    fun filter(source: BaseItemDto, candidates: List<BaseItemDto>): List<BaseItemDto> {
        if (source.Genres.isEmpty()) return emptyList()
        val wanted = source.Genres.map { it.lowercase() }.toSet()
        val kept = candidates.filter { c ->
            c.Id != source.Id && c.Type == source.Type && c.Genres.any { it.lowercase() in wanted }
        }
        return if (kept.size >= MIN_ITEMS) kept else emptyList()
    }
}
