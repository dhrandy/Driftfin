package tv.jetflix.phone

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.jetflix.phone.data.AuthenticateUserByName
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.TokenStore

/** The very first request of a sign-in carries this install's persistent device ID, never a placeholder, and never an old token. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColdAuthHeaderTest {
    private fun server() = MockWebServer().also { s ->
        s.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                MockResponse().setHeader("Content-Type", "application/json").setBody("""{"AccessToken":"t","User":{"Id":"u","Name":"N"},"Secret":"s","Code":"123456"}""")
        }
        s.start()
    }

    private fun idOf(h: String) = Regex("DeviceId=\"([^\"]*)\"").find(h)!!.groupValues[1]

    @Test fun passwordAndQuickConnectUseStoreIdWithNoToken() = runBlocking {
        Graph.init(ApplicationProvider.getApplicationContext())
        Graph.signOut()
        val store = Graph.tokens.deviceId()
        val s = server()
        val base = s.url("/").toString().trimEnd('/')
        Graph.repo.apiFor(base).authenticateByName(AuthenticateUserByName("u", "p"))
        Graph.repo.apiFor(base).quickConnectInitiate()
        val h1 = s.takeRequest().getHeader("Authorization")!!
        val h2 = s.takeRequest().getHeader("Authorization")!!
        s.shutdown()
        for (h in listOf(h1, h2)) {
            assertEquals(store, idOf(h))
            assertTrue(store.startsWith("driftfin-phone-"))
            assertFalse("no placeholder id: $h", h.contains("DeviceId=\"jetflix\""))
            assertFalse("no token before sign-in: $h", h.contains("Token="))
        }
    }

    @Test fun oldTokenIsNotSentToADifferentServer() = runBlocking {
        Graph.init(ApplicationProvider.getApplicationContext())
        val a = server(); val b = server()
        val baseA = a.url("/").toString().trimEnd('/'); val baseB = b.url("/").toString().trimEnd('/')
        SessionManager.signIn(Session(baseA, "u", "N", "oldtoken", Graph.tokens.deviceId()))
        Graph.repo.apiFor(baseB).authenticateByName(AuthenticateUserByName("u", "p"))
        val toB = b.takeRequest().getHeader("Authorization")!!
        Graph.repo.apiFor(baseA).quickConnectInitiate()
        val toA = a.takeRequest().getHeader("Authorization")!!
        a.shutdown(); b.shutdown()
        Graph.signOut()
        assertFalse("old token must not reach another server: $toB", toB.contains("oldtoken"))
        assertEquals(Graph.tokens.deviceId(), idOf(toB))
        assertTrue("signed-in API is unchanged: $toA", toA.contains("Token=\"oldtoken\""))
    }

    @Test fun idStableOnSameInstallAndDistinctAcrossInstalls() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val one = TokenStore(ctx).deviceId()
        assertEquals(one, TokenStore(ctx).deviceId())
        ctx.getSharedPreferences("driftfin_phone_session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        assertNotEquals(one, TokenStore(ctx).deviceId())
    }
}
