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
        tokenStore.load()?.let { session ->
            SessionManager.signIn(session)
            repo.apiFor(session.serverUrl)
        }
    }

    /** Coil image loader that authenticates image requests with the Jellyfin token. */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", SessionManager.authorizationHeader())
                        .build(),
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .respectCacheHeaders(false)
            .build()
    }
}
