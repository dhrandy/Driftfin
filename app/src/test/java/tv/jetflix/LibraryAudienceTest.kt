package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.BaseItemDto
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.ui.components.JetflixTab
import tv.jetflix.app.ui.library.Audience
import tv.jetflix.app.ui.library.isKidsLibrary
import tv.jetflix.app.ui.library.LibraryPrefsStore
import tv.jetflix.app.ui.library.LibraryViewModel
import tv.jetflix.app.ui.library.allows
import tv.jetflix.app.ui.library.isVideoLibrary

/** All / Kids only / No kids on the Shows and Movies tabs. */
class LibraryAudienceTest {
    private fun lib(name: String, type: String?) = BaseItemDto(Id = name, Name = name, Type = "CollectionFolder", CollectionType = type)
    private val views = listOf(lib("Movies", "movies"), lib("Kids Movies", "movies"), lib("TV Shows", "tvshows"), lib("Kids Shows", "tvshows"), lib("Music", "music"))

    private fun pick(a: Audience) = views.filter { isVideoLibrary(it) }.filter { a.allows(it) }.map { it.Name }

    @Test fun allKeepsEveryVideoLibrary() = assertEquals(listOf("Movies", "Kids Movies", "TV Shows", "Kids Shows"), pick(Audience.All))
    @Test fun kidsOnlyKeepsOnlyKidsLibraries() = assertEquals(listOf("Kids Movies", "Kids Shows"), pick(Audience.KidsOnly))
    @Test fun onlyTheTwoNamedKidsLibrariesCount() {
        assertEquals(false, isKidsLibrary(lib("Kidney Stories", "movies")))
        assertEquals(false, isKidsLibrary(lib("Skid Row Docs", "tvshows")))
        assertEquals(true, isKidsLibrary(lib(" kids shows ", "tvshows")))
    }
    @Test fun noKidsDropsKidsLibrariesButKeepsTheRest() = assertEquals(listOf("Movies", "TV Shows"), pick(Audience.NoKids))
    @Test fun modesNeverMixKindsBecauseTheKindComesFromTheTab() {
        // The tab, not the audience, decides includeItemTypes (Movie vs Series); audience only chooses which libraries are searched.
        assertTrue(Audience.values().all { a -> pick(a).none { it == "Music" } })
    }

    private class MemStore : LibraryPrefsStore {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    @Test fun choiceIsSavedPerTabAndClearsTheLibraryPick() {
        val store = MemStore()
        val a = LibraryViewModel(JellyfinRepository(), store)
        a.restorePrefs("u1", JetflixTab.MOVIES)
        a.setLibrary(JetflixTab.MOVIES, "lib-x")
        a.setAudience(JetflixTab.MOVIES, Audience.NoKids)
        a.persistPrefs("u1", JetflixTab.MOVIES)
        assertEquals(null, a.state.value.libraryId)
        val b = LibraryViewModel(JellyfinRepository(), store); b.restorePrefs("u1", JetflixTab.MOVIES)
        assertEquals(Audience.NoKids, b.state.value.audience)
        val shows = LibraryViewModel(JellyfinRepository(), store); shows.restorePrefs("u1", JetflixTab.SHOWS)
        assertEquals(Audience.All, shows.state.value.audience)
    }
}
