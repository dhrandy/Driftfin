package tv.jetflix.phone.data

import tv.jetflix.phone.Graph

/** Reads the lists the screens show. Every call returns an empty list on failure, so a screen can show "nothing here" instead of crashing. */
object Content {
    private val userId get() = SessionManager.session.value?.userId.orEmpty()
    private val api get() = Graph.repo.apiFor(SessionManager.session.value?.serverUrl.orEmpty())

    private suspend fun <T> safe(default: T, block: suspend () -> T): T = try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { default }

    /** Jellyfin's own list of trailer files for a title; a failed lookup just hides the Trailer button. */
    suspend fun localTrailers(itemId: String): List<BaseItemDto> = safe(emptyList()) { api.getLocalTrailers(itemId, userId).body().orEmpty() }

    suspend fun resume(): List<BaseItemDto> = safe(emptyList()) { api.getResumeItems(userId).body()?.Items.orEmpty() }
    suspend fun nextUp(): List<BaseItemDto> = safe(emptyList()) { api.getNextUp(userId).body()?.Items.orEmpty() }
    suspend fun latest(type: String): List<BaseItemDto> = safe(emptyList()) { api.getLatest(userId, includeItemTypes = type).body().orEmpty() }

    /** Unwatched movies with a good rating, best first. The Home hero picks from these. */
    suspend fun heroCandidates(): List<BaseItemDto> = safe(emptyList()) {
        // RemoteTrailers carries the banner's Trailer button: Jellyfin only sends those links when the field
        // is requested, so without it the button never appears even for movies that have trailer links.
        api.getItems(userId, includeItemTypes = "Movie", recursive = true, sortBy = "CommunityRating", sortOrder = "Descending",
            limit = 50, filters = "IsUnplayed", fields = "Overview,Genres,RemoteTrailers").body()?.Items.orEmpty()
    }

    suspend fun browse(type: String, sort: Sort, unwatchedOnly: Boolean, genres: Collection<String>? = null, libraryId: String? = null): List<BaseItemDto> = safe(emptyList()) {
        val found = api.getItems(userId, parentId = libraryId, includeItemTypes = type, recursive = true, sortBy = sort.apiField, sortOrder = sort.order, limit = 500,
            filters = if (unwatchedOnly) "IsUnplayed" else null, genres = genres?.takeIf { it.isNotEmpty() }?.joinToString(","), fields = "Genres,ProductionYear,CommunityRating").body()?.Items.orEmpty()
        // The server is asked to sort, but these orders are enforced here too: a server that drops or
        // misapplies the sort can't leak its own default order (reported 10/29: Movies Year came out alphabetical).
        when (sort) {
            Sort.Year -> found.sortedByDescending { it.ProductionYear ?: Int.MIN_VALUE }
            Sort.Rating -> found.sortedByDescending { it.CommunityRating ?: Float.MIN_VALUE }
            else -> found
        }
    }

    /** The server's video libraries (Movies, Shows, Kids...) for the Browse library filter. Music, books and the like never appear. */
    suspend fun libraries(): List<BaseItemDto> = safe(emptyList()) {
        api.getUserViews(userId).body()?.Items.orEmpty().filter { isVideoLibrary(it) }
    }

    suspend fun search(term: String): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, includeItemTypes = "Movie,Series", recursive = true, searchTerm = term, limit = 60).body()?.Items.orEmpty()
    }

    /** Genres for the Categories filter, scoped to the picked library (a Kids library only offers kids genres). */
    suspend fun genres(type: String, libraryId: String? = null): List<String> = safe(emptyList()) { api.getItemFilters(userId, parentId = libraryId, includeItemTypes = type).body()?.Genres.orEmpty().sorted() }

    /** A show's seasons, in order. Empty when the server keeps no Season entries - the page then lists all episodes flat. */
    suspend fun seasons(seriesId: String): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, parentId = seriesId, includeItemTypes = "Season", sortBy = "SortName", limit = 100).body()?.Items.orEmpty()
    }

    suspend fun byPerson(personId: String): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, includeItemTypes = "Movie,Series", recursive = true, personIds = personId, sortBy = "SortName", limit = 200).body()?.Items.orEmpty()
    }

    /** Idle Search rows: the server's suggestions, else the best-rated unwatched titles. Only titles with a poster. */
    suspend fun recommended(type: String): List<BaseItemDto> = safe(emptyList()) {
        val suggested = try { api.getSuggestions(userId, type, limit = 24).body()?.Items } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        val list = if (!suggested.isNullOrEmpty()) suggested else
            api.getItems(userId, includeItemTypes = type, recursive = true, sortBy = "CommunityRating", sortOrder = "Descending", limit = 40, filters = "IsUnplayed").body()?.Items.orEmpty()
        list.filter { Graph.repo.posterUrl(it) != null }.take(20)
    }

    suspend fun collections(): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, includeItemTypes = "BoxSet", recursive = true, sortBy = "SortName", fields = "Overview,ChildCount", limit = 300).body()?.Items.orEmpty()
    }

    suspend fun collectionItems(id: String): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, parentId = id, fields = "Overview,Genres", limit = 300).body()?.Items.orEmpty()
    }

    /** Driftfin's own Watchlist: starred ids fetched fresh; the default order is newest star first. Only an answer that returns items prunes ids the server lost. */
    suspend fun watchlist(sort: Sort = Sort.Added): List<BaseItemDto> = safe(emptyList()) {
        val starred = Watchlist.ids.value
        if (starred.isEmpty()) emptyList() else {
            // The sorts below read these from the items themselves; a fetch without them sorts nothing (reported 10/29: Year came out alphabetical).
            val found = api.getItems(userId, includeItemTypes = "Movie,Series", recursive = true, ids = starred.joinToString(","), fields = "Overview,Genres,ProductionYear,CommunityRating").body()?.Items.orEmpty()
            if (found.isNotEmpty()) Watchlist.pruneMissing(found.map { it.Id }.toSet())
            orderWatchlist(found, starred, sort)
        }
    }

    /** Watchlist ordering: "Recently added" is star recency (newest star first); the other sorts read the items, unknowns last. */
    fun orderWatchlist(found: List<BaseItemDto>, starred: List<String>, sort: Sort): List<BaseItemDto> = when (sort) {
        Sort.Added -> found.sortedBy { starred.indexOf(it.Id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        Sort.Name -> found.sortedBy { it.Name.orEmpty().lowercase() }
        Sort.Rating -> found.sortedByDescending { it.CommunityRating ?: Float.MIN_VALUE }
        Sort.Year -> found.sortedByDescending { it.ProductionYear ?: Int.MIN_VALUE }
    }

    suspend fun favorites(sort: Sort = Sort.Name): List<BaseItemDto> = safe(emptyList()) {
        api.getItems(userId, includeItemTypes = "Movie,Series", recursive = true, filters = "IsFavorite", sortBy = sort.apiField, sortOrder = sort.order, limit = 300).body()?.Items.orEmpty()
    }

    suspend fun item(id: String): BaseItemDto? = safe(null) { api.getItem(id, userId).body() }
    suspend fun episodes(seriesId: String): List<BaseItemDto> = safe(emptyList()) { api.getEpisodes(seriesId, userId, fields = "Overview,PremiereDate", limit = 2000).body()?.Items.orEmpty() }
    suspend fun setFavorite(id: String, on: Boolean): Boolean = safe(false) {
        (if (on) api.addFavorite(id, userId) else api.removeFavorite(id, userId)).isSuccessful
    }
}

/** Same rule as the TV app: everything that can hold films or shows counts, including mixed collections such as a Kids library. */
fun isVideoLibrary(view: BaseItemDto) = view.CollectionType?.lowercase() !in setOf("music", "musicvideos", "books", "livetv", "playlists", "photos", "boxsets", "trailers")

/** A library belongs on a browse tab when its collection kind matches the tab ("movies" under
 *  Movies, "tvshows" under Shows). A library with no declared kind can hold both, so it stays on both. */
fun matchesKind(view: BaseItemDto, type: String): Boolean = when (view.CollectionType?.lowercase()) {
    "movies" -> type == "Movie"
    "tvshows" -> type == "Series"
    else -> true
}

enum class Sort(val label: String, val apiField: String, val order: String) {
    Added("Recently added", "DateCreated", "Descending"),
    Name("Name", "SortName", "Ascending"),
    Rating("Rating", "CommunityRating", "Descending"),
    // The same field list the TV app's Release Year sort sends his server; the year leads, ties fall to name.
    Year("Year", "ProductionYear,PremiereDate,SortName", "Descending"),
}
