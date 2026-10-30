package tv.jetflix.phone

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.TokenCrypto
import tv.jetflix.phone.data.TokenStore

/**
 * A saved login must survive the app being closed and reopened. Process death is simulated by
 * dropping every in-memory object (a fresh [TokenStore] over the same preferences file); only
 * an explicit sign-out, not a restart, may return the app to the login screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneSessionPersistenceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val base = "http://one.invalid:1"

    /** A fresh store over this install's preferences, like the one a new process would create. */
    private fun freshStore() = TokenStore(ctx, object : TokenCrypto {
        override fun encrypt(plain: String) = "x:$plain"
        override fun decrypt(blob: String) = blob.removePrefix("x:")
    })

    @Before fun cleanSlate() {
        Graph.init(ctx)
        ctx.getSharedPreferences("driftfin_phone_session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Graph.tokens = freshStore()
        SessionManager.signOut() // in-memory only; the store is what a restart rereads
    }

    @Test fun savedLoginSurvivesSimulatedProcessDeath() {
        assertTrue(Graph.tokens.save(base, "u1", "Test User", "tok1"))
        Graph.signIn(Graph.tokens.load()!!)

        // New process: nothing in memory, only what was written to disk.
        SessionManager.signOut()
        val restored = freshStore().load()
        assertNotNull("a saved login must still be there after a restart", restored)
        assertEquals("u1", restored!!.userId)
        assertEquals("tok1", restored.token)
        assertEquals(Graph.tokens.deviceId(), restored.deviceId)
        Graph.signIn(restored)
        assertEquals("u1", SessionManager.session.value?.userId)
    }

    @Test fun onlyExplicitSignOutReturnsToLoginScreen() {
        Graph.tokens.save(base, "u1", "Test User", "tok1")
        // Close and reopen without signing out: still signed in.
        assertNotNull(freshStore().load())
        Graph.signOut()
        assertNull("after an explicit sign-out the next launch must show the login screen", freshStore().load())
    }

    @Test fun signOutKeepsProfilePickableWithoutPassword() {
        Graph.tokens.save(base, "u1", "Test User", "tok1")
        Graph.signOut()
        val saved = freshStore().profiles(base)
        assertEquals("the signed-out profile stays saved for one-tap re-entry", 1, saved.size)
        assertTrue(freshStore().activate(saved.first()))
        assertEquals("u1", freshStore().load()!!.userId)
    }

    @Test fun lastActiveProfileIsTheOneRestoredAfterRestart() {
        Graph.tokens.save(base, "u1", "Test User", "tok1")
        Graph.tokens.save(base, "u2", "Guest", "tok2") // most recent sign-in is active
        assertEquals("u2", freshStore().load()!!.userId)
        val ava = freshStore().profiles(base).first { it.userId == "u1" }
        assertTrue(freshStore().activate(ava))
        assertEquals("u1", freshStore().load()!!.userId)
    }

    @Test fun forgetDropsTheProfileEntirely() {
        Graph.tokens.save(base, "u1", "Test User", "tok1")
        freshStore().forget(base, "u1")
        assertTrue(freshStore().profiles(base).isEmpty())
        assertNull(freshStore().load())
    }
}
