package tv.jetflix.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.Watchlist

/** Driftfin's own Watchlist store: per server+user, newest starred first, persists, 500 cap. */
@RunWith(RobolectricTestRunner::class)
class PhoneWatchlistTest {
    @Before fun setUp() {
        SessionManager.signOut()
        Watchlist.resetForTest()
        Watchlist.init(RuntimeEnvironment.getApplication())
        Watchlist.setUser("http://s1", "u1")
    }

    @Test fun starAndUnstar() {
        assertTrue(Watchlist.toggle("a"))
        assertTrue(Watchlist.contains("a"))
        assertFalse(Watchlist.toggle("a"))
        assertFalse(Watchlist.contains("a"))
    }

    @Test fun newestStarredFirst() {
        Watchlist.toggle("a"); Watchlist.toggle("b"); Watchlist.toggle("c")
        assertEquals(listOf("c", "b", "a"), Watchlist.ids.value)
    }

    @Test fun listsAreIsolatedPerServerAndUser() {
        Watchlist.toggle("a")
        Watchlist.setUser("http://s1", "u2"); assertTrue(Watchlist.ids.value.isEmpty())
        Watchlist.setUser("http://s2", "u1"); assertTrue(Watchlist.ids.value.isEmpty())
        Watchlist.setUser("http://s1", "u1"); assertEquals(listOf("a"), Watchlist.ids.value)
    }

    @Test fun survivesSignOutAndReload() {
        Watchlist.toggle("a"); Watchlist.toggle("b")
        Watchlist.setUser(null, null)
        assertTrue(Watchlist.ids.value.isEmpty())
        Watchlist.setUser("http://s1", "u1")
        assertEquals(listOf("b", "a"), Watchlist.ids.value)
    }

    @Test fun signInAndOutMoveTheList() {
        SessionManager.signIn(Session("http://s9", "u9", "n", "t", "d"))
        Watchlist.toggle("x")
        assertTrue(Watchlist.contains("x"))
        SessionManager.signOut()
        assertFalse(Watchlist.contains("x"))
        SessionManager.signIn(Session("http://s9", "u9", "n", "t", "d"))
        assertTrue("the stored list comes back for the same user", Watchlist.contains("x"))
    }

    @Test fun fiveHundredthStarFitsTheNextIsRefused() {
        repeat(Watchlist.MAX) { assertTrue(Watchlist.toggle("id$it")) }
        assertEquals(Watchlist.MAX, Watchlist.ids.value.size)
        assertFalse(Watchlist.toggle("one-too-many"))
        assertFalse(Watchlist.contains("one-too-many"))
        assertEquals(Watchlist.MAX, Watchlist.ids.value.size)
    }
}
