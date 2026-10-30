package tv.jetflix.phone

import android.content.Context
import coil.ImageLoader
import tv.jetflix.phone.data.JellyfinRepository
import tv.jetflix.phone.data.Session
import tv.jetflix.phone.data.SessionManager
import tv.jetflix.phone.data.TokenStore

/** The few objects the whole app shares. Set up once from the Application. */
object Graph {
    val repo = JellyfinRepository()
    lateinit var tokens: TokenStore
        internal set // tests swap in a stand-in cipher
    lateinit var images: ImageLoader
        private set

    /** Tests draw to a software canvas, which cannot show hardware bitmaps. Always true in the app. */
    internal var allowHardwareArt = true

    private lateinit var app: Context
    fun appContext(): Context = app

    fun init(context: Context) {
        if (::tokens.isInitialized) return
        app = context.applicationContext
        tokens = TokenStore(app)
        SessionManager.deviceIdProvider = { tokens.deviceId() }
        tv.jetflix.phone.data.Watchlist.init(app)
        tv.jetflix.phone.data.PhoneSettings.init(app)
        // Artwork requests carry the sign-in token to the signed-in server only.
        buildImages()
        tokens.load()?.let { signIn(it) }
    }

    private fun buildImages() {
        images = ImageLoader.Builder(app).okHttpClient(SessionManager.authenticatedClientBuilder().build()).crossfade(allowHardwareArt).allowHardware(allowHardwareArt).build()
    }

    /** Tests only: rebuild the image loader after changing [allowHardwareArt], because tests share one JVM. */
    internal fun rebuildImagesForTest() { buildImages() }

    fun signIn(session: Session) {
        SessionManager.signIn(session)
        repo.apiFor(session.serverUrl)
    }

    fun signOut() {
        tokens.clear()
        SessionManager.signOut()
    }
}
