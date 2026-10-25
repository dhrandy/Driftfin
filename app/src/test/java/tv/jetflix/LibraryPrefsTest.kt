package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.LibraryPrefsStore
import tv.jetflix.app.ui.library.LibrarySort
import tv.jetflix.app.ui.library.LibraryViewModel
import tv.jetflix.app.ui.library.WatchFilter

/** Sort and filter choices come back per user and per section (JVM, in-memory store). */
class LibraryPrefsTest {
    private class MemStore : LibraryPrefsStore {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    private fun vm(store: LibraryPrefsStore) = LibraryViewModel(JellyfinRepository(), store)

    @Test fun choicesComeBackForTheSameUserAndSection() {
        val store = MemStore()
        val a = vm(store)
        a.restorePrefs("u1", JetflixTab.MOVIES)
        a.setSort(JetflixTab.MOVIES, LibrarySort.Rating)   // load() returns early: no session in a unit test
        a.setReversed(JetflixTab.MOVIES, true)
        a.setGenre(JetflixTab.MOVIES, "Comedy")
        a.setYear(JetflixTab.MOVIES, 1999)
        a.cycleWatch(JetflixTab.MOVIES)
        a.setLibrary(JetflixTab.MOVIES, "lib-kids")
        a.persistPrefs("u1", JetflixTab.MOVIES)

        val b = vm(store)
        b.restorePrefs("u1", JetflixTab.MOVIES)
        val s = b.state.value
        assertEquals(LibrarySort.Rating, s.sort)
        assertEquals(true, s.reversed)
        assertEquals("Comedy", s.genre)
        assertEquals(1999, s.year)
        assertEquals(WatchFilter.Unwatched, s.watch)
        assertEquals("lib-kids", s.libraryId)
    }

    @Test fun anotherUserOrSectionStartsClean() {
        val store = MemStore()
        val a = vm(store)
        a.restorePrefs("u1", JetflixTab.MOVIES)
        a.setSort(JetflixTab.MOVIES, LibrarySort.Rating)
        a.setGenre(JetflixTab.MOVIES, "Comedy")
        a.persistPrefs("u1", JetflixTab.MOVIES)

        val otherUser = vm(store); otherUser.restorePrefs("u2", JetflixTab.MOVIES)
        assertEquals(LibrarySort.Name, otherUser.state.value.sort); assertNull(otherUser.state.value.genre)
        val otherSection = vm(store); otherSection.restorePrefs("u1", JetflixTab.SHOWS)
        assertEquals(LibrarySort.Name, otherSection.state.value.sort); assertNull(otherSection.state.value.genre)
    }

    @Test fun collectionContentsAreNotSaved() {
        val store = MemStore()
        val a = vm(store)
        a.loadCollection("box1") // no session in a unit test, so only the collection mode is set
        a.setSort(JetflixTab.MY_LIST, LibrarySort.Rating)
        a.persistPrefs("u1", JetflixTab.MY_LIST)
        assertEquals(emptyMap<String, String>(), store.m)
    }
}
