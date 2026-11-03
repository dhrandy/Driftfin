package tv.jetflix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.jetflix.app.data.ProfileScope

class ProfileScopeTest {

    private val randy = "https://jellyfin.home" to "user-randy"
    private val heather = "https://jellyfin.home" to "user-heather"

    @Test fun keyIsStableAndProfileSpecific() {
        val a = ProfileScope.key("autoplay_next", randy.first, randy.second)
        assertEquals(a, ProfileScope.key("autoplay_next", randy.first, randy.second))
        assertNotEquals(a, ProfileScope.key("autoplay_next", heather.first, heather.second))
        assertNotEquals(a, ProfileScope.key("skip_intro", randy.first, randy.second))
        assertTrue(a.startsWith("autoplay_next_"))
    }

    @Test fun sameUserDifferentServersStaySeparate() {
        assertNotEquals(
            ProfileScope.key("autoplay_next", "https://server-a", "user-randy"),
            ProfileScope.key("autoplay_next", "https://server-b", "user-randy"),
        )
    }

    @Test fun readFallsBackToLegacySharedKeyUntilProfileOverrides() {
        // Migration: the value saved before per-profile settings existed sits under the plain key.
        val store = mutableMapOf("autoplay_next" to false)
        val scoped = ProfileScope.key("autoplay_next", randy.first, randy.second)

        // Randy never touched it after the upgrade: he reads the legacy value, nothing is lost.
        assertEquals("autoplay_next", ProfileScope.readKey("autoplay_next", scoped, store::containsKey))

        // Randy changes it: his own key is written and read; the legacy key is untouched, so
        // Heather (own scoped key absent) still reads the legacy value.
        store[scoped] = true
        assertEquals(scoped, ProfileScope.readKey("autoplay_next", scoped, store::containsKey))
        val heatherScoped = ProfileScope.key("autoplay_next", heather.first, heather.second)
        assertEquals("autoplay_next", ProfileScope.readKey("autoplay_next", heatherScoped, store::containsKey))
    }

    @Test fun signedOutReadsTheSharedKey() {
        // With no active profile the scoped key IS the base key, so the shared value is used.
        val store = mapOf("autoplay_next" to false)
        assertEquals("autoplay_next", ProfileScope.readKey("autoplay_next", "autoplay_next", store::containsKey))
    }
}
