package tv.jetflix

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.jetflix.app.data.AppSettings
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import java.lang.reflect.Proxy

/**
 * Drives the real AppSettings + SessionManager sign-in/switch/sign-out paths against a stub
 * SharedPreferences, proving profile isolation and the legacy-fallback migration without a device.
 */
class AppSettingsProfileIsolationTest {

    private val store = HashMap<String, Any?>()

    private fun stubPrefs(): SharedPreferences {
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, m, args ->
            when (m.name) {
                "putBoolean", "putString", "putInt", "putLong", "putFloat" -> store[args[0] as String] = args[1]
                "remove" -> store.remove(args[0] as String)
                "clear" -> store.clear()
            }
            if (m.name == "commit") true else null
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, m, args ->
            val k = args?.get(0) as? String
            when (m.name) {
                "getBoolean" -> store[k] as? Boolean ?: args[1] as Boolean
                "getString" -> store[k] as? String ?: args[1] as String?
                "getInt" -> store[k] as? Int ?: args[1] as Int
                "contains" -> store.containsKey(k)
                "edit" -> editor
                "getAll" -> store.toMap()
                else -> null
            }
        } as SharedPreferences
    }

    @Before
    fun setUp() {
        store.clear()
        SessionManager.signOut()
        val f = AppSettings::class.java.getDeclaredField("prefs")
        f.isAccessible = true
        f.set(AppSettings, stubPrefs())
        AppSettings.reloadProfileSettings()
    }

    private fun signIn(user: String) =
        SessionManager.signIn(Session("https://srv", user, user, "tok", "dev"))

    @Test fun legacyValueCarriesToEveryProfileUntilOverridden() {
        // Pre-upgrade state: a shared plain-key choice exists.
        AppSettings.setAutoplayNext(false) // signed out: writes the legacy plain key
        assertEquals(false, store["autoplay_next"])

        signIn("randy")
        assertFalse(AppSettings.autoplayNext.value) // legacy fallback, nothing lost

        AppSettings.setAutoplayNext(true)
        assertEquals(false, store["autoplay_next"]) // legacy key untouched
        assertTrue(store.keys.single { it.startsWith("autoplay_next_") }.let { store[it] as Boolean })

        signIn("heather")
        assertFalse(AppSettings.autoplayNext.value) // she still inherits the legacy value

        SessionManager.signOut()
        assertFalse(AppSettings.autoplayNext.value) // signed out reads the shared value
    }

    @Test fun profilesDoNotLeakIntoEachOtherOrIntoTheSharedKeys() {
        signIn("randy")
        AppSettings.setSkipIntro(false)
        AppSettings.audioLanguage = "fra"
        assertFalse(store.containsKey("skip_intro"))
        assertFalse(store.containsKey("audio_language"))

        signIn("heather")
        assertTrue(AppSettings.skipIntro.value) // randy's choice does not leak
        assertEquals("eng", AppSettings.audioLanguage)

        AppSettings.setSkipIntro(true) // her write must not resurrect randy's
        signIn("randy")
        assertFalse(AppSettings.skipIntro.value)
        assertEquals("fra", AppSettings.audioLanguage)
    }

    @Test fun explicitSubtitleOffBeatsLegacyLanguageForThatProfileOnly() {
        AppSettings.subtitleLanguage = "fre" // signed out: legacy shared choice
        signIn("randy")
        assertEquals("fre", AppSettings.subtitleLanguage) // inherited

        AppSettings.subtitleLanguage = null // explicit OFF for randy
        assertNull(AppSettings.subtitleLanguage)
        AppSettings.reloadProfileSettings()
        assertNull(AppSettings.subtitleLanguage) // still off after a re-read: no fallback resurrection

        signIn("heather")
        assertEquals("fre", AppSettings.subtitleLanguage) // others keep the shared choice

        SessionManager.signOut()
        assertEquals("fre", AppSettings.subtitleLanguage)
    }

    @Test fun signedOutWritesUseTheSharedKeysAsBefore() {
        SessionManager.signOut()
        AppSettings.setHomeBanner(false)
        AppSettings.subtitleLanguage = "ita"
        assertEquals(false, store["home_banner"])
        assertEquals("ita", store["subtitle_language"])
        assertTrue(store.keys.none { it != "home_banner" && it != "subtitle_language" })
    }
}
