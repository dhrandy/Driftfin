package tv.jetflix.phone

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.ProfileSwitch
import tv.jetflix.phone.data.SessionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSwitchTest {
    private fun setup(code: Int): Triple<MockWebServer, String, Unit> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Graph.init(ctx)
        // Robolectric has no Android Keystore, so saved logins use a stand-in cipher.
        Graph.tokens = tv.jetflix.phone.data.TokenStore(ctx, object : tv.jetflix.phone.data.TokenCrypto {
            override fun encrypt(plain: String) = "x:$plain"
            override fun decrypt(blob: String) = blob.removePrefix("x:")
        })
        ctx.getSharedPreferences("driftfin_phone_session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.signOut()
        val s = MockWebServer(); s.enqueue(MockResponse().setResponseCode(code).setBody("{}")); s.start()
        val base = s.url("/").toString().trimEnd('/')
        Graph.tokens.save(base, "ua", "Ava", "tokA")
        Graph.tokens.save(base, "ub", "Ben", "tokB")
        Graph.signIn(Graph.tokens.load()!!) // Ben is signed in
        return Triple(s, base, Unit)
    }

    @Test fun savedListsBothLoginsForThisServerOnly() {
        val (s, base, _) = setup(200)
        Graph.tokens.save("http://other.invalid:1", "uz", "Zed", "tokZ")
        Graph.signIn(Graph.tokens.profiles(base).first { it.userId == "ub" })
        assertEquals(listOf("Ava", "Ben"), ProfileSwitch.saved().map { it.userName })
        s.shutdown(); Graph.signOut()
    }

    @Test fun acceptedLoginSwitchesWithoutPasswordAndKeepsTheOther() {
        val (s, base, _) = setup(200)
        val ava = ProfileSwitch.saved().first { it.userName == "Ava" }
        assertEquals(ProfileSwitch.Outcome.Switched, ProfileSwitch.select(ava))
        assertEquals("Ava", SessionManager.session.value?.userName)
        assertEquals("Ava", Graph.tokens.load()?.userName)
        assertTrue(ProfileSwitch.saved().any { it.userName == "Ben" })
        s.shutdown(); Graph.signOut()
    }

    @Test fun rejectedLoginIsDroppedAndSessionUnchanged() {
        val (s, _, _) = setup(401)
        val ava = ProfileSwitch.saved().first { it.userName == "Ava" }
        assertEquals(ProfileSwitch.Outcome.Expired("Ava"), ProfileSwitch.select(ava))
        assertEquals("Ben", SessionManager.session.value?.userName)
        assertNull(ProfileSwitch.saved().firstOrNull { it.userName == "Ava" })
        s.shutdown(); Graph.signOut()
    }

    @Test fun unreachableServerChangesNothing() {
        val (s, _, _) = setup(503)
        val ava = ProfileSwitch.saved().first { it.userName == "Ava" }
        assertEquals(ProfileSwitch.Outcome.Unreachable("Ava"), ProfileSwitch.select(ava))
        assertEquals("Ben", SessionManager.session.value?.userName)
        assertTrue(ProfileSwitch.saved().any { it.userName == "Ava" })
        s.shutdown(); Graph.signOut()
    }
}
