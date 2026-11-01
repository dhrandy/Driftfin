package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.jetflix.app.ui.library.LibrarySort
import tv.jetflix.app.ui.library.gridSortBy

/**
 * User report: "date added isn't working on the shows, the last thing that was added isn't showing
 * first." A series keeps the DateCreated of when the show first landed; new episodes don't bump it.
 * The Shows tab's Date Added sort therefore follows DateLastContentAdded (bumps when an episode is
 * added under the series), falling back to DateCreated. Movies and mixed queries are unchanged.
 */
class TvDateAddedSortTest {
    @Test fun showsDateAddedFollowsNewestEpisode() {
        assertEquals("DateLastContentAdded,DateCreated,SortName", gridSortBy(LibrarySort.Added, "Series"))
    }

    @Test fun moviesDateAddedUnchanged() {
        assertEquals("DateCreated,SortName", gridSortBy(LibrarySort.Added, "Movie"))
    }

    @Test fun mixedQueriesKeepDateCreated() {
        // DateLastContentAdded exists only on series; a mixed Movie,Series fetch must not use it.
        assertEquals("DateCreated,SortName", gridSortBy(LibrarySort.Added, "Movie,Series"))
    }

    @Test fun otherSortsUnchangedOnShows() {
        LibrarySort.values().filter { it != LibrarySort.Added }.forEach {
            assertEquals(it.sortBy, gridSortBy(it, "Series"))
        }
    }
}
