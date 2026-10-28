package tv.jetflix.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import tv.jetflix.app.ui.components.rememberFocusMemory
import tv.jetflix.app.ui.components.restoreFocus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.ui.components.fadeUnderMenuBar
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.components.PosterCard
import tv.jetflix.app.ui.components.TopNavBar
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

/** Orders Jellyfin supports for the Shows and Movies grids. */
enum class LibrarySort(val label: String, val sortBy: String, val order: String) {
    Name("Name", "SortName", "Ascending"),
    Added("Date Added", "DateCreated,SortName", "Descending"),
    Released("Release Year", "ProductionYear,PremiereDate,SortName", "Descending"),
    Rating("Rating", "CommunityRating,SortName", "Descending"),
    PlayCount("Play Count", "PlayCount,SortName", "Descending"),
}

/** Watched filter, as in the Jellyfin IsPlayed / IsUnplayed item filters. */
enum class WatchFilter(val label: String, val jellyfin: String?) {
    All("All", null), Unwatched("Unwatched", "IsUnplayed"), Watched("Watched", "IsPlayed");
    fun next() = values()[(ordinal + 1) % values().size]
}

/** Sections inside My Driftfin, in his order: Favorites (Jellyfin heart), Watchlist (Driftfin's own star), Collections (box sets). */
enum class MySection(val label: String) { Favorites("Favorites"), Watchlist("Watchlist"), Collections("Collections") }

data class LibraryUiState(
    val sort: LibrarySort = LibrarySort.Name,
    val reversed: Boolean = false,
    val section: MySection = MySection.Favorites,
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<BaseItemDto> = emptyList(),
    /** Random title nobody has started, for the banner on Movies and Shows. Null hides the banner. */
    val banner: BaseItemDto? = null,
    val watch: WatchFilter = WatchFilter.All,
    /** Only titles with a 4K version (Movies/Shows tabs). */
    val only4k: Boolean = false,
    val genre: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    /** Video libraries on the server that hold titles of this tab's kind (id to name). The picker shows when there is more than one. */
    val libraries: List<Pair<String, String>> = emptyList(),
    val libraryId: String? = null,
)

/** Libraries that can hold movies or shows. Mixed, untyped and custom-typed libraries count too; music, books, photos and live TV do not. */
fun isVideoLibrary(view: BaseItemDto) = view.CollectionType?.lowercase() !in setOf("music", "musicvideos", "books", "livetv", "playlists", "photos", "boxsets", "trailers")

/** Where Library sort and filter choices are saved. Keys already carry the user and section. */
interface LibraryPrefsStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

object AppSettingsLibraryStore : LibraryPrefsStore {
    override fun get(key: String) = tv.jetflix.app.data.AppSettings.libraryPrefGet(key)
    override fun put(key: String, value: String?) = tv.jetflix.app.data.AppSettings.libraryPrefPut(key, value)
}

/** Grid of every Movie (or Series) across all libraries of that type. */
class LibraryViewModel(val repo: JellyfinRepository, private val store: LibraryPrefsStore = AppSettingsLibraryStore) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state = _state.asStateFlow()

    private var sort = LibrarySort.Name
    private var reversed = false
    private var section = MySection.Favorites
    private var bannerTab: JetflixTab? = null
    private var libraryId: String? = null
    private var knownLibraries = emptyList<Pair<String, String>>()
    private var collectionId: String? = null
    private var watch = WatchFilter.All
    private var only4k = false
    private var genre: String? = null
    private var year: Int? = null
    private var knownGenres = emptyList<String>()
    private var knownYears = emptyList<Int>()

    private var restored = false

    private fun key(userId: String, tab: JetflixTab, name: String) = "lib.$userId.${tab.name}.$name"

    /** Saves the current sort and filters for this user and section. Collections (box set contents) are not saved. */
    internal fun persistPrefs(userId: String, tab: JetflixTab) {
        if (collectionId != null) return
        store.put(key(userId, tab, "sort"), sort.name)
        store.put(key(userId, tab, "reversed"), reversed.toString())
        store.put(key(userId, tab, "watch"), watch.name)
        store.put(key(userId, tab, "4k"), if (only4k) "1" else null)
        store.put(key(userId, tab, "genre"), genre)
        store.put(key(userId, tab, "year"), year?.toString())
        store.put(key(userId, tab, "library"), libraryId)
    }

    /** Brings back what this user last chose in this section. Runs once, before the first load. */
    internal fun restorePrefs(userId: String, tab: JetflixTab) {
        if (restored || collectionId != null) return
        restored = true
        sort = LibrarySort.values().firstOrNull { it.name == store.get(key(userId, tab, "sort")) } ?: sort
        reversed = store.get(key(userId, tab, "reversed")) == "true"
        watch = WatchFilter.values().firstOrNull { it.name == store.get(key(userId, tab, "watch")) } ?: watch
        only4k = store.get(key(userId, tab, "4k")) == "1"
        genre = store.get(key(userId, tab, "genre"))
        year = store.get(key(userId, tab, "year"))?.toIntOrNull()
        libraryId = store.get(key(userId, tab, "library"))
        if (tab != JetflixTab.MY_LIST) bannerTab = tab // keeps the restored library choice on the first load
        _state.value = _state.value.copy(sort = sort, reversed = reversed, watch = watch, only4k = only4k, genre = genre, year = year, libraryId = libraryId)
    }

    private fun save(tab: JetflixTab) { SessionManager.session.value?.let { persistPrefs(it.userId, tab) } }

    private fun filterString(extra: String? = null) = listOfNotNull(extra, watch.jellyfin).joinToString(",").ifEmpty { null }

    /** Genre and year choices come from the server's own list for the whole library, never from the filtered items. */
    private suspend fun loadOptions(tab: JetflixTab, api: tv.jetflix.app.data.JellyfinApi, userId: String, parents: List<String?>, types: String) {
        if (knownGenres.isNotEmpty() || knownYears.isNotEmpty()) return
        val all = parents.mapNotNull { p -> try { api.getItemFilters(userId, p, types).body() } catch (_: Exception) { null } }
        knownGenres = all.flatMap { it.Genres }.distinct().sorted()
        knownYears = all.flatMap { it.Years }.distinct().sortedDescending()
    }

    /** A saved genre or year the server no longer lists would hide everything, so it is cleared. */
    private fun dropStaleFilters(): Boolean {
        var changed = false
        if (genre != null && knownGenres.isNotEmpty() && genre !in knownGenres) { genre = null; changed = true }
        if (year != null && knownYears.isNotEmpty() && year !in knownYears) { year = null; changed = true }
        return changed
    }

    private fun order(): String = if (reversed) (if (sort.order == "Ascending") "Descending" else "Ascending") else sort.order

    private fun fresh(loading: Boolean = false, error: String? = null, items: List<BaseItemDto> = emptyList(), banner: BaseItemDto? = null) =
        LibraryUiState(sort = sort, reversed = reversed, section = section, loading = loading, error = error, items = items, banner = banner, watch = watch, only4k = only4k, genre = genre, year = year, genres = knownGenres, years = knownYears, libraries = knownLibraries, libraryId = libraryId)

    fun cycleWatch(tab: JetflixTab) { watch = watch.next(); _state.value = _state.value.copy(watch = watch); save(tab); load(tab) }
    fun set4k(tab: JetflixTab, value: Boolean) { if (value == only4k) return; only4k = value; _state.value = _state.value.copy(only4k = value); save(tab); load(tab) }
    fun setLibrary(tab: JetflixTab, id: String?) { libraryId = id; _state.value = _state.value.copy(libraryId = id); save(tab); load(tab) }
    fun setGenre(tab: JetflixTab, value: String?) { genre = value; _state.value = _state.value.copy(genre = value); save(tab); load(tab) }
    fun setYear(tab: JetflixTab, value: Int?) { year = value; _state.value = _state.value.copy(year = value); save(tab); load(tab) }

    fun setReversed(tab: JetflixTab, value: Boolean) {
        if (value == reversed) return
        reversed = value
        _state.value = _state.value.copy(reversed = value)
        save(tab)
        load(tab)
    }

    fun setSection(tab: JetflixTab, newSection: MySection) {
        if (newSection == section) return
        section = newSection
        _state.value = fresh(loading = true)
        load(tab)
    }

    /** Items inside one Jellyfin collection (box set), in the server's own order unless a sort is picked. */
    fun loadCollection(id: String) {
        collectionId = id
        load(JetflixTab.MY_LIST)
    }

    fun setSort(tab: JetflixTab, newSort: LibrarySort) {
        if (newSort == sort) return
        sort = newSort
        _state.value = _state.value.copy(sort = newSort)
        save(tab)
        load(tab)
    }

    fun load(tab: JetflixTab) {
        val session = SessionManager.session.value ?: return
        val api = repo.apiFor(session.serverUrl)
        restorePrefs(session.userId, tab)
        val includeTypes = if (tab == JetflixTab.MOVIES) "Movie" else "Series"
        viewModelScope.launch {
            if (_state.value.items.isEmpty()) _state.value = fresh(loading = true)
            try {
                val inCollection = collectionId
                if (inCollection != null) {
                    val kids = api.getItems(
                        userId = session.userId,
                        parentId = inCollection,
                        sortBy = sort.sortBy,
                        sortOrder = order(),
                        fields = "Overview,Genres",
                        filters = filterString(), genres = genre, years = year?.toString(),
                    ).body()?.Items ?: emptyList()
                    loadOptions(tab, api, session.userId, listOf(null), "Movie,Series")
                    _state.value = fresh(items = kids)
                    return@launch
                }
                if (tab == JetflixTab.MY_LIST) {
                    val mine = when (section) {
                        MySection.Collections -> api.getItems(
                            userId = session.userId,
                            includeItemTypes = "BoxSet",
                            recursive = true,
                            sortBy = sort.sortBy,
                            sortOrder = order(),
                            fields = "Overview,Genres,ChildCount",
                            filters = filterString(), genres = genre, years = year?.toString(),
                        ).body()?.Items ?: emptyList()
                        // Driftfin's own Watchlist: the starred ids, fetched fresh so posters/watched state
                        // are current, shown in star order (newest first) like Marquee's shelf.
                        MySection.Watchlist -> {
                            val starred = tv.jetflix.app.data.Watchlist.ids.value
                            if (starred.isEmpty()) emptyList() else {
                                val found = api.getItems(
                                    userId = session.userId,
                                    includeItemTypes = "Movie,Series",
                                    recursive = true,
                                    ids = starred.joinToString(","),
                                    fields = "Overview,Genres",
                                ).body()?.Items ?: emptyList()
                                val rank = starred.withIndex().associate { it.value to it.index }
                                // The fetch has no filters, so a missing id means the server lost it: drop it.
                                tv.jetflix.app.data.Watchlist.pruneMissing(found.map { it.Id }.toSet())
                                found.sortedBy { rank[it.Id] ?: Int.MAX_VALUE }
                            }
                        }
                        MySection.Favorites -> api.getItems(
                            userId = session.userId,
                            includeItemTypes = "Movie,Series",
                            recursive = true,
                            sortBy = sort.sortBy,
                            sortOrder = order(),
                            fields = "Overview,Genres",
                            filters = filterString("IsFavorite"), genres = genre, years = year?.toString(),
                        ).body()?.Items ?: emptyList()
                    }
                    loadOptions(tab, api, session.userId, listOf(null), "Movie,Series")
                    if (dropStaleFilters()) { save(tab); load(tab); return@launch }
                    _state.value = fresh(items = mine)
                    return@launch
                }
                if (bannerTab != tab) { bannerTab = tab; libraryId = null; knownLibraries = emptyList() }
                val views = api.getUserViews(session.userId).body()?.Items ?: emptyList()
                val candidates = views.filter { isVideoLibrary(it) }
                val wanted = if (libraryId != null) candidates.filter { it.Id == libraryId }.ifEmpty { libraryId = null; candidates } else candidates
                val perView = wanted.map { view ->
                    view to async {
                        api.getItems(
                            userId = session.userId,
                            parentId = view.Id,
                            includeItemTypes = includeTypes,
                            recursive = true,
                            sortBy = sort.sortBy,
                            sortOrder = order(),
                            fields = "Overview,Genres",
                            filters = filterString(), genres = genre, years = year?.toString(),
                            is4K = if (only4k && tab == JetflixTab.MOVIES) true else null,
                        ).body()?.Items ?: emptyList()
                    }
                }.map { (v, d) -> v to d.await() }
                val mergedAll = perView.flatMap { it.second }.distinctBy { it.Id }
                // Shows have no server-side series 4K filter: a series stays when the server reports any 4K episode in it.
                // A failed episode lookup is an error, not an unfiltered list - otherwise non-4K shows would appear under "4K only".
                val merged = if (only4k && tab == JetflixTab.SHOWS) {
                    val perLib = wanted.map { view ->
                        async {
                            try {
                                api.getItems(
                                    userId = session.userId, parentId = view.Id, includeItemTypes = "Episode",
                                    recursive = true, is4K = true, fields = "", enableImages = false, enableUserData = false, limit = 4000,
                                ).body()?.Items?.mapNotNull { it.SeriesId }?.toSet()
                            } catch (_: Exception) { null }
                        }
                    }.map { it.await() }
                    if (perLib.any { it == null }) {
                        _state.value = fresh(error = "Couldn't check which shows have a 4K version. Change a filter or reopen the tab to try again.")
                        return@launch
                    } else {
                        val keep = perLib.filterNotNull().flatten().toSet()
                        mergedAll.filter { it.Id in keep }
                    }
                } else mergedAll
                if (libraryId == null && watch == WatchFilter.All && genre == null && year == null) {
                    knownLibraries = perView.filter { it.second.isNotEmpty() }.map { it.first.Id to (it.first.Name ?: "Library") }
                }
                loadOptions(tab, api, session.userId, wanted.map { it.Id }, includeTypes)
                if (dropStaleFilters()) { save(tab); load(tab); return@launch }
                _state.value = fresh(items = merged)
            } catch (e: Exception) {
                _state.value = fresh(error = "Couldn't load (${e.javaClass.simpleName})")
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    tab: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onUserClick: () -> Unit,
    onPlay: (BaseItemDto) -> Unit = {},
    collectionId: String? = null,
    collectionTitle: String? = null,
) {
    val state by viewModel.state.collectAsState()
    LibraryContent(
        state, viewModel.repo, tab, userName, onTabSelected, onItemClick, onUserClick,
        onSort = { viewModel.setSort(tab, it) }, onPlay = onPlay,
        onReverse = { viewModel.setReversed(tab, it) }, onSection = { viewModel.setSection(tab, it) },
        onWatch = { viewModel.cycleWatch(tab) }, on4k = { viewModel.set4k(tab, it) }, onGenre = { viewModel.setGenre(tab, it) }, onYear = { viewModel.setYear(tab, it) }, onLibrary = { viewModel.setLibrary(tab, it) },
        collectionTitle = collectionTitle,
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    repo: JellyfinRepository,
    tab: JetflixTab,
    userName: String?,
    onTabSelected: (JetflixTab) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onUserClick: () -> Unit,
    onSort: (LibrarySort) -> Unit = {},
    onPlay: (BaseItemDto) -> Unit = {},
    onReverse: (Boolean) -> Unit = {},
    onSection: (MySection) -> Unit = {},
    onWatch: () -> Unit = {},
    on4k: (Boolean) -> Unit = {},
    onGenre: (String?) -> Unit = {},
    onYear: (Int?) -> Unit = {},
    onLibrary: (String?) -> Unit = {},
    collectionTitle: String? = null,
) {
    val focusMemory = rememberFocusMemory()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val startedFresh = remember { !focusMemory.hasKey }
    // Captured at composition: the sweep handoff clears the live flag when the tab takes focus, and content
    // arriving after that must not pull focus down. Only Down or OK enters content after a sweep.
    val openedBySweep = remember {
        val sweeping = tv.jetflix.app.ui.components.NavSweep.active
        // A remembered card from an earlier visit restores focus when its grid card composes; on a sweep
        // arrival that card composes after the handoff has cleared the live flag, so its restoreFocus guard
        // no longer protects the tab. Consume the pending restore here, at screen composition.
        if (sweeping) focusMemory.pending = false
        sweeping
    }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty() && startedFresh && !openedBySweep) {
            try { firstFocus.requestFocus() } catch (_: Exception) { }
        }
    }
    val sortFocus = remember { FocusRequester() }
    // Attached to the card the user last focused (focusMemory.key); that card is composed whenever the
    // grid is scrolled, so it is a live Down target from the bar when the sort/section row is gone.
    val rememberedCardFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    var picker by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    if (picker == "sort") {
        val ascending = (state.sort.order == "Ascending") != state.reversed
        OptionPicker(
            "Sort by",
            LibrarySort.values().map { it.label to "sort:${it.name}" } + listOf("Ascending" to "order:asc", "Descending" to "order:desc"),
            "sort:${state.sort.name}", { picker = null }, allLabel = null,
        ) { v ->
            when {
                v == null -> {}
                v.startsWith("sort:") -> onSort(LibrarySort.valueOf(v.removePrefix("sort:")))
                v == "order:asc" -> if (!ascending) onReverse(!state.reversed)
                v == "order:desc" -> if (ascending) onReverse(!state.reversed)
            }
            picker = null
        }
    } else if (picker == "filter") {
        OptionPicker(
            "Filter",
            listOfNotNull(
                "Watched: ${state.watch.label}" to "watch",
                if (tab == JetflixTab.MOVIES || tab == JetflixTab.SHOWS) "Resolution: ${if (state.only4k) "4K only" else "All"}" to "4k" else null,
                "Genre: ${state.genre ?: "All"}" to "genre",
                "Year: ${state.year ?: "All"}" to "year",
                "Reset filters" to "reset",
            ),
            null, { picker = null }, allLabel = null, columns = 2,
        ) { v ->
            when (v) {
                "watch" -> onWatch()
                "4k" -> on4k(!state.only4k)
                "genre" -> picker = "genre"
                "year" -> picker = "year"
                "reset" -> {
                    onGenre(null); onYear(null); on4k(false)
                    repeat(when (state.watch) { WatchFilter.All -> 0; WatchFilter.Unwatched -> 2; WatchFilter.Watched -> 1 }) { onWatch() }
                    picker = null
                }
                else -> {}
            }
        }
    } else if (picker == "library") {
        OptionPicker("Library", state.libraries.map { it.second to it.first }, state.libraryId, { picker = null }, columns = 2) { onLibrary(it); picker = null }
    } else if (picker == "genre") {
        OptionPicker("Genre", state.genres.map { it to it }, state.genre, { picker = null }) { onGenre(it); picker = null }
    } else if (picker == "year") {
        OptionPicker("Year", state.years.map { it.toString() to it }, state.year, { picker = null }) { onYear(it); picker = null }
    }
    var focusedItem by remember { androidx.compose.runtime.mutableStateOf<BaseItemDto?>(null) }
    // Artwork warmer, on the same image loader the cards use, so a focus move finds its art already cached.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val artworkPrefetcher = androidx.compose.runtime.remember {
        val loader = coil.Coil.imageLoader(appContext)
        tv.jetflix.app.ui.components.ArtworkPrefetcher(
            requestFor = { spec -> tv.jetflix.app.ui.components.artworkRequest(appContext, spec) },
            enqueue = { loader.enqueue(it) },
        )
    }
    // Warm-up stops with the screen; nothing speculative keeps running after leaving the grid.
    androidx.compose.runtime.DisposableEffect(artworkPrefetcher) {
        onDispose { artworkPrefetcher.cancelAll() }
    }
    // The pixel size the grid's cards ask for, so the warmed bitmaps satisfy their requests from memory.
    val gridCardPx = run {
        val scale = tv.jetflix.app.data.AppSettings.posterSize.value.scale
        val w = (160 * scale * appContext.resources.displayMetrics.density).toInt()
        tv.jetflix.app.ui.components.ArtworkSpec("", w, (w * 3f / 2f).toInt())
    }
    val navFocus = remember { FocusRequester() }
    var navHasFocus by remember { androidx.compose.runtime.mutableStateOf(false) }
    // True once the grid has moved off the very top: the sort/section row is (or will be) disposed, so
    // nothing may hard-point at its focus requester - a detached target swallows the move entirely.
    val scrolledAway by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    // Back from the grid goes to the top menu first, as on most TV apps; Back from the menu leaves.
    androidx.activity.compose.BackHandler(enabled = !navHasFocus && collectionTitle == null) {
        try { navFocus.requestFocus() } catch (_: Exception) { }
    }
    val isMine = tab == JetflixTab.MY_LIST && collectionTitle == null
    val showWatchlistSection by tv.jetflix.app.data.AppSettings.showWatchlist.collectAsState()
    if (!showWatchlistSection && state.section == MySection.Watchlist) LaunchedEffect(Unit) { onSection(MySection.Favorites) }
    val showSort = state.section != MySection.Watchlist && (tab != JetflixTab.MY_LIST || collectionTitle != null || isMine) && (state.items.isNotEmpty() || state.sort != LibrarySort.Name || isMine || state.watch != WatchFilter.All || state.only4k || state.genre != null || state.year != null)
    val sectionFocus = remember { FocusRequester() }
    // the banner belongs on Home only; Movies and Shows start straight at the sort and poster grid.
    val banner: BaseItemDto? = null
    val bannerFocus = remember { FocusRequester() }
    LaunchedEffect(banner?.Id) {
        if (banner != null && startedFresh && !openedBySweep) {
            repeat(10) { try { bannerFocus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } }
        }
    }
    // Focus below the banner scrolls the banner fully out of view, so its text is never left cut off under the menu bar.
    val hideScope = androidx.compose.runtime.rememberCoroutineScope()
    val hideBanner: () -> Unit = {
        if (banner != null && showSort && gridState.firstVisibleItemIndex == 0) hideScope.launch { gridState.animateScrollToItem(1) }
    }
    Box(Modifier.fillMaxSize().background(JetflixColors.Background)) {
        if (state.items.isNotEmpty() || isMine) {
            val barPx = with(androidx.compose.ui.platform.LocalDensity.current) { 136.dp.toPx() }
            val bottomPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
            val scrollSpec = remember(barPx, bottomPx) { tv.jetflix.app.ui.home.MenuAwareScrollSpec(barPx, bottomPx) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides scrollSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(tv.jetflix.app.data.AppSettings.posterSize.collectAsState().value.gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize()
                    .fadeUnderMenuBar(scrolledAway),
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 92.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (banner != null) {
                    item(key = "banner", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        tv.jetflix.app.ui.home.HeroBanner(
                            hero = banner,
                            backdropUrl = repo.backdropUrl(banner, 1920),
                            logoUrl = repo.logoUrl(banner),
                            onPlay = onPlay,
                            onMoreInfo = onItemClick,
                            playFocus = bannerFocus,
                            upFocus = navFocus,
                            downFocus = if (showSort) sortFocus else null,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                            height = 400.dp,
                        )
                    }
                }
                if (collectionTitle != null) {
                    item(key = "collectionTitle", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(collectionTitle, style = JetflixType.heroTitle, color = JetflixColors.TextPrimary, maxLines = 1)
                    }
                }
                if (isMine) {
                    item(key = "sections", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.focusProperties { down = sortFocus; up = navFocus },
                        ) {
                            MySection.values().filter { it != MySection.Watchlist || showWatchlistSection }.forEach { sec ->
                                val selected = sec == state.section
                                androidx.tv.material3.Button(
                                    onClick = { onSection(sec) },
                                    modifier = if (selected) Modifier.focusRequester(sectionFocus) else Modifier,
                                    colors = androidx.tv.material3.ButtonDefaults.colors(
                                        containerColor = if (selected) androidx.compose.ui.graphics.Color(0xFF3A3A3A) else androidx.compose.ui.graphics.Color.Transparent,
                                        contentColor = androidx.compose.ui.graphics.Color.White,
                                        focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                                        focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                                    ),
                                ) { Text(sec.label, style = JetflixType.body) }
                            }
                        }
                    }
                }
                if (showSort) {
                    item(key = "sort", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .onFocusChanged { if (it.hasFocus) hideBanner() }
                                .then(if (startedFresh && state.items.isNotEmpty()) Modifier.focusProperties { down = firstFocus } else Modifier)
                                .then(if (banner != null) Modifier.focusProperties { up = bannerFocus } else Modifier)
                                .then(if (isMine) Modifier.focusProperties { up = sectionFocus } else Modifier)
                                // Plain Movies/Shows had no Up target here: default search landed on the Search
                                // icon instead of the selected tab (Randy's report on build 20).
                                .then(if (banner == null && !isMine) Modifier.focusProperties { up = navFocus } else Modifier),
                        ) {
                            val ascending = (state.sort.order == "Ascending") != state.reversed
                            val active = (if (state.watch != WatchFilter.All) 1 else 0) + (if (state.only4k) 1 else 0) + (if (state.genre != null) 1 else 0) + (if (state.year != null) 1 else 0)
                            val chipColors = @Composable { on: Boolean ->
                                androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = if (on) androidx.compose.ui.graphics.Color(0xFF3A3A3A) else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor = androidx.compose.ui.graphics.Color.White,
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                                    focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                                )
                            }
                            androidx.tv.material3.Button(
                                onClick = { picker = "sort" },
                                modifier = Modifier.focusRequester(sortFocus),
                                colors = chipColors(true),
                            ) { Text("Sort: ${state.sort.label} ${if (ascending) "\u2191" else "\u2193"}", style = JetflixType.body) }
                            androidx.tv.material3.Button(
                                onClick = { picker = "filter" },
                                modifier = Modifier.focusRequester(filterFocus),
                                colors = chipColors(active > 0),
                            ) { Text(if (active > 0) "Filter ($active)" else "Filter", style = JetflixType.body) }
                            if (state.libraries.size > 1) {
                                androidx.tv.material3.Button(
                                    onClick = { picker = "library" },
                                    colors = chipColors(state.libraryId != null),
                                ) { Text("Library: ${state.libraries.firstOrNull { it.first == state.libraryId }?.second ?: "All"}", style = JetflixType.body) }
                            }
                        }
                    }
                }
                itemsIndexed(state.items, key = { _, it -> it.Id }) { index, item ->
                    PosterCard(
                        item = item,
                        imageUrl = repo.posterUrl(item),
                        modifier = (if (showSort && index < tv.jetflix.app.data.AppSettings.posterSize.value.gridColumns) Modifier.focusProperties { up = sortFocus } else Modifier)
                            .onFocusChanged { if (it.hasFocus) {
                                focusedItem = item
                                hideBanner()
                                val columns = tv.jetflix.app.data.AppSettings.posterSize.value.gridColumns
                                artworkPrefetcher.prefetch(
                                    listOfNotNull(repo.backdropUrl(item, 960)?.let { tv.jetflix.app.ui.components.ArtworkSpec(tv.jetflix.app.ui.components.blurSource(it)) }) +
                                        listOfNotNull(
                                            state.items.getOrNull(index + 1),
                                            state.items.getOrNull(index + 2),
                                            state.items.getOrNull(index + columns),
                                        ).mapNotNull { repo.posterUrl(it)?.let { u -> gridCardPx.copy(url = u) } },
                                )
                            } }
                            .then(if (focusMemory.key == item.Id) Modifier.focusRequester(rememberedCardFocus) else Modifier)
                            .then(if (startedFresh && item.Id == state.items.first().Id) {
                                Modifier.focusRequester(firstFocus).restoreFocus(focusMemory, item.Id)
                            } else {
                                Modifier.restoreFocus(focusMemory, item.Id)
                            }),
                        onClick = { onItemClick(item) },
                        width = 160.dp * tv.jetflix.app.data.AppSettings.posterSize.value.scale,
                    )
                }
            }
            }
        } else if (!state.loading) {
            Text(
                state.error ?: if (tab == JetflixTab.MY_LIST) (when (state.section) { MySection.Collections -> "No collections found on this server."; MySection.Watchlist -> "Nothing on your watchlist yet. Open a movie or show and choose the star."; MySection.Favorites -> "No favorites yet. Open a title and choose Add to Favorites." }) else "No ${tab.label.lowercase()} found.",
                style = JetflixType.body,
                color = JetflixColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(48.dp),
            )
        }
        TopNavBar(
            solid = true,
            selected = tab,
            userName = userName,
            onTabSelected = onTabSelected,
            onUserClick = onUserClick,
            modifier = Modifier.align(Alignment.TopCenter),
            downFocus = if (banner != null) bannerFocus else if (scrolledAway) (if (focusMemory.hasKey) rememberedCardFocus else null) else if (isMine) sectionFocus else if (showSort) sortFocus else null,
            selectedFocus = navFocus,
            blurArtUrl = (focusedItem ?: banner)?.let { repo.backdropUrl(it, 960) },
            onNavFocusChange = { navHasFocus = it },
        )
    }
}

/** Pick one value (or All) from the server's full list. Back closes it without changing anything. */
@Composable
fun <T> OptionPicker(title: String, options: List<Pair<String, T>>, selected: T?, onDismiss: () -> Unit, allLabel: String? = "All", columns: Int = 4, onPick: (T?) -> Unit) {
    val focus = remember { FocusRequester() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .background(androidx.compose.ui.graphics.Color(0xFF222222), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = JetflixType.sectionTitle, color = androidx.compose.ui.graphics.Color.White)
            if (options.isEmpty()) Text("The server returned no ${title.lowercase()} values.", style = JetflixType.body, color = JetflixColors.TextSecondary)
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
                modifier = Modifier.height(if (allLabel == null) 220.dp else 320.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val all: List<Pair<String, T?>> = (if (allLabel != null) listOf<Pair<String, T?>>(allLabel to null) else emptyList()) + options
                items(all.size) { i ->
                    val (label, value) = all[i]
                    val isSel = value == selected
                    androidx.tv.material3.Button(
                        onClick = { onPick(value) },
                        modifier = if (isSel || (i == 0 && all.none { it.second == selected })) Modifier.focusRequester(focus) else Modifier,
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = if (isSel) androidx.compose.ui.graphics.Color(0xFF3A3A3A) else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                        ),
                    ) { Text(label, style = JetflixType.body, maxLines = 1) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { repeat(10) { try { focus.requestFocus(); return@LaunchedEffect } catch (_: Exception) { kotlinx.coroutines.delay(60) } } }
}
