package tv.jetflix

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.TokenCrypto
import tv.jetflix.app.data.TokenStore
import tv.jetflix.app.ui.login.LoginViewModel

/** Synthetic users and tokens, a local fake server, and a stand-in for the Keystore. No real accounts. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileSwitchTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val fake = object : TokenCrypto {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(blob: String) = if (blob.startsWith("enc:")) blob.removePrefix("enc:").reversed() else null
    }
    private fun store() = TokenStore(ctx, fake)
    private lateinit var server: MockWebServer
    private val seen = mutableListOf<RecordedRequest>()
    private var meCode = 200

    @Before fun up() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ctx.getSharedPreferences("jetflix_session", Context.MODE_PRIVATE).edit().clear().commit()
        SessionManager.signOut()
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request
                    return when {
                        request.path!!.startsWith("/Users/Me") -> MockResponse().setResponseCode(meCode).setBody("{}")
                        request.path!!.startsWith("/Users/Public") -> MockResponse().setBody("""[{"Name":"Alex","Id":"u1"},{"Name":"Sam","Id":"u2"}]""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
    }
    @After fun down() { server.shutdown(); Dispatchers.resetMain(); SessionManager.signOut() }

    private fun url() = server.url("/").toString().trimEnd('/')
    private fun vm(s: TokenStore): LoginViewModel { s.saveLastServer(url()); return LoginViewModel(JellyfinRepository(), s) }
    private fun waitIdle(v: LoginViewModel) { val end = System.currentTimeMillis() + 5000; while (v.state.value.connecting && System.currentTimeMillis() < end) Thread.sleep(20) }

    @Test fun savedProfilesSurviveSwitchingAndRelaunch() {
        val s = store()
        s.save(url(), "u1", "Alex", "tokenA"); s.save(url(), "u2", "Sam", "tokenB")
        assertEquals("Sam is the active login", "Sam", s.load()?.userName)
        s.clear() // Switch profile
        assertNull(s.load())
        val relaunched = store() // a new TokenStore over the same saved data, as after a restart
        assertEquals(listOf("Alex", "Sam"), relaunched.profiles(url()).map { it.userName })
        assertEquals("tokenA", relaunched.profiles(url()).first { it.userName == "Alex" }.token)
    }

    @Test fun anOlderActiveLoginOnlyInstallStaysPickableAfterSwitching() {
        val prefs = ctx.getSharedPreferences("jetflix_session", Context.MODE_PRIVATE)
        val s = store()
        s.save(url(), "u1", "Alex", "tokenA")
        prefs.edit().remove(prefs.all.keys.first { it.startsWith("profile_") }).commit() // like an install that predates saved profiles
        assertTrue(s.profiles(url()).any { it.userName == "Alex" })
        s.clear()
        assertEquals(listOf("Alex"), store().profiles(url()).map { it.userName })
    }

    @Test fun profilesAreSeparatedByServerAndForgetRemovesOnlyThatOne() {
        val s = store()
        s.save(url(), "u1", "Alex", "tokenA"); s.save("http://other.test:8096", "u1", "Alex", "tokenOther"); s.save(url(), "u2", "Sam", "tokenB")
        assertEquals(setOf("tokenA", "tokenB"), s.profiles(url()).map { it.token }.toSet())
        assertEquals(listOf("tokenOther"), s.profiles("http://other.test:8096").map { it.token })
        s.forget(url(), "u1")
        assertEquals(listOf("Sam"), s.profiles(url()).map { it.userName })
        assertEquals(listOf("tokenOther"), s.profiles("http://other.test:8096").map { it.token })
    }

    @Test fun nothingSecretIsStoredInTheClear() {
        store().save(url(), "u1", "Alex", "tokenA-secret")
        val all = ctx.getSharedPreferences("jetflix_session", Context.MODE_PRIVATE).all.values.joinToString("|")
        assertFalse(all.contains("tokenA-secret"))
    }

    @Test fun pickingASavedProfileSignsInWithItsOwnTokenAndNoPassword() {
        val s = store(); s.save(url(), "u1", "Alex", "tokenA"); s.save(url(), "u2", "Sam", "tokenB"); s.clear()
        val v = vm(s)
        assertEquals(listOf("Alex", "Sam"), v.state.value.savedProfiles)
        var done = false
        v.selectProfile("Alex") { done = true }; waitIdle(v)
        assertTrue(done)
        assertEquals("Alex", SessionManager.session.value?.userName)
        assertEquals("tokenA", SessionManager.session.value?.token)
        assertEquals("u1", s.load()?.userId)
        val me = seen.last { it.path!!.startsWith("/Users/Me") }
        assertTrue(me.getHeader("Authorization")!!.contains("Token=\"tokenA\""))
        assertFalse(seen.any { it.path!!.contains("AuthenticateByName") })
    }

    @Test fun anExpiredSavedLoginAsksForThePasswordInsteadOfSigningIn() {
        meCode = 401
        val s = store(); s.save(url(), "u1", "Alex", "tokenA"); s.clear()
        val v = vm(s)
        var done = false
        v.selectProfile("Alex") { done = true }; waitIdle(v)
        assertFalse(done); assertNull(SessionManager.session.value)
        assertTrue(v.state.value.error!!.contains("expired"))
        assertTrue("dead token is dropped", s.profiles(url()).isEmpty())
        assertEquals("Alex", v.state.value.username)
    }

    @Test fun offlineKeepsTheSavedLoginAndDoesNotSignIn() {
        val s = store(); s.save(url(), "u1", "Alex", "tokenA"); s.clear()
        val v = vm(s)
        server.shutdown()
        var done = false
        v.selectProfile("Alex") { done = true }; waitIdle(v)
        assertFalse(done); assertNull(SessionManager.session.value)
        assertEquals(1, s.profiles(url()).size)
        assertNotNull(v.state.value.error)
    }

    @Test fun pickingAnUnsavedNameJustFillsTheUsername() {
        val s = store(); s.save(url(), "u1", "Alex", "tokenA"); s.clear()
        val v = vm(s)
        var done = false
        v.selectProfile("Sam") { done = true }
        assertFalse(done); assertEquals("Sam", v.state.value.username); assertNull(SessionManager.session.value)
    }

    @Test fun tokenIsNotSentToARedirectTarget() {
        val other = MockWebServer().apply { start() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(302).setHeader("Location", other.url("/Users/Me").toString())
        }
        val r = JellyfinRepository().validateToken(Session(url(), "u1", "Alex", "tokenA", "dev"))
        assertNull("a redirect is not an accepted token", r)
        assertEquals(0, other.requestCount)
        other.shutdown()
    }
}
