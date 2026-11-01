package tv.jetflix.app.data

/** Recommendation rows should not offer titles the user has already watched. */
object RecommendationFilter {
    /** Fewer suggested titles than this after filtering (i.e. none) and the page falls back to the unplayed list, as before. */
    const val MIN_SUGGESTED = 1

    /** Fail closed: an item is kept only when the server confirmed it is unplayed. */
    fun onlyIn(items: List<BaseItemDto>, unplayedIds: Set<String>): List<BaseItemDto> = items.filter { it.Id in unplayedIds }

}
