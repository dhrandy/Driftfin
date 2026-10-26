package tv.jetflix

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager

class AuthHeaderScopeTest {
    private val home = MockWebServer()
    private val foreign = MockWebServer()

    @After fun tearDown() {
        SessionManager.signOut()
        home.shutdown()
        foreign.shutdown()
    }

    private fun signIn(url: String) = SessionManager.signIn(
        Session(serverUrl = url, userId = "u", userName = "demo", token = "TESTTOKEN", deviceId = "d"),
    )

    private fun authorized(url: String) = SessionManager.authorize(Request.Builder().url(url).build()).header("Authorization")

    @Test fun sameOriginGetsTheToken() {
        signIn("http://media.test:8096")
        assertEquals(true, authorized("http://media.test:8096/Items/1/Images/Primary")?.contains("TESTTOKEN"))
    }

    @Test fun otherHostPortOrSchemeNeverGetsTheToken() {
        signIn("https://media.test")
        assertNull(authorized("https://elsewhere.test/sub.vtt"))
        assertNull(authorized("https://media.test:8443/x"))
        assertNull("https to http downgrade", authorized("http://media.test/x"))
        signIn("http://media.test:8096")
        assertNull(authorized("https://media.test:8096/x"))
    }

    @Test fun realClientSendsTokenToServerButNotToForeignOriginOrRedirectTarget() {
        home.start(); foreign.start()
        signIn(home.url("/").toString().trimEnd('/'))
        val client = SessionManager.authenticatedClientBuilder().build()

        home.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(home.url("/img")).build()).execute().close()
        assertNotNull(home.takeRequest().getHeader("Authorization"))

        foreign.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(foreign.url("/sub.vtt")).build()).execute().close()
        assertNull(foreign.takeRequest().getHeader("Authorization"))

        // The server answers with a redirect to another origin: the follow-up must not carry the token.
        home.enqueue(MockResponse().setResponseCode(302).setHeader("Location", foreign.url("/landing").toString()))
        foreign.enqueue(MockResponse().setBody("ok"))
        client.newCall(Request.Builder().url(home.url("/redirect")).build()).execute().close()
        assertNotNull(home.takeRequest().getHeader("Authorization"))
        assertNull(foreign.takeRequest().getHeader("Authorization"))
    }
}
