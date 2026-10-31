package tv.jetflix.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.TokenStore

class JetflixApp : Application(), ImageLoaderFactory {

    lateinit var repo: JellyfinRepository
        private set
    lateinit var tokenStore: TokenStore
        private set

    override fun onCreate() {
        super.onCreate()
        repo = JellyfinRepository()
        tokenStore = TokenStore(this)
        tv.jetflix.app.data.AppSettings.init(this)
        tv.jetflix.app.data.Watchlist.init(this)
        tokenStore.load()?.let { session ->
            SessionManager.signIn(session)
            repo.apiFor(session.serverUrl)
        }
    }

    /** Coil image loader that authenticates image requests with the Jellyfin token. */
    override fun newImageLoader(): ImageLoader {
        // OkHttp's default 5-requests-per-host cap serialises poster rows: a screen of posters
        // loads noticeably faster when more fetches run at once (measured against a live server).
        val imageDispatcher = okhttp3.Dispatcher().apply { maxRequestsPerHost = 12 }
        val client = SessionManager.authenticatedClientBuilder()
            .dispatcher(imageDispatcher)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .respectCacheHeaders(false)
            .build()
    }
}
