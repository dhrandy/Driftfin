package tv.jetflix.app.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class JellyfinRepository {

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var api: JellyfinApi? = null
    private var apiDeviceId: String? = null
    var serverUrl: String? = null
        private set

    /** Normalize user input into a base server URL: add scheme, strip trailing slash and /web. */
    fun normalizeServerUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) url = "http://$url"
        url = url.trimEnd('/')
        if (url.endsWith("/web")) url = url.dropLast(4)
        return url
    }

    /**
     * Asks the saved profile's own server whether its token still works, without touching the active session.
     * true = accepted, false = rejected (expired or revoked), null = could not tell (offline, server error).
     * The token is sent only to that profile's server and redirects are not followed.
     */
    fun validateToken(session: Session, client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()): Boolean? = try {
        val url = (session.serverUrl.trimEnd('/') + "/Users/Me").toHttpUrlOrNull() ?: return null
        val req = okhttp3.Request.Builder().url(url).header("Authorization", SessionManager.authorizationHeader(session.token, session.deviceId)).build()
        client.newCall(req).execute().use { r ->
            when {
                r.isSuccessful -> true
                r.code == 401 || r.code == 403 -> false
                else -> null
            }
        }
    } catch (_: Exception) { null }

    fun apiFor(baseUrl: String, deviceId: String? = null): JellyfinApi {
        val existing = api
        if (existing != null && serverUrl == baseUrl && apiDeviceId == deviceId) return existing
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", SessionManager.authorizationHeader(deviceId = deviceId, includeToken = deviceId == null))
                    .build()
                chain.proceed(req)
            }
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(JellyfinApi::class.java).also {
            api = it
            apiDeviceId = deviceId
            serverUrl = baseUrl
        }
    }

    /** Direct-play stream URL for an item (original file, no transcoding). */
    fun streamUrl(itemId: String, mediaSourceId: String? = null): String {
        val base = serverUrl ?: return ""
        return "$base/Videos/$itemId/stream?static=true&mediaSourceId=${mediaSourceId ?: itemId}"
    }

    fun imageUrl(itemId: String, type: String, maxWidth: Int? = null, tag: String? = null, index: Int? = null): String {
        val base = serverUrl ?: return ""
        val sb = StringBuilder("$base/Items/$itemId/Images/$type")
        if (index != null) sb.append("/").append(index)
        val params = mutableListOf<String>()
        if (maxWidth != null) params += "maxWidth=$maxWidth"
        if (tag != null) params += "tag=$tag"
        // WebP is roughly a quarter of the bytes of the server's default JPEG at poster sizes
        // (measured on a real library), and Android decodes it natively on every supported API
        // level (minSdk 26). Logos are left alone: they carry transparency, and a server without
        // a WebP encoder would flatten them to JPEG - the poster win isn't worth that corner.
        if (type != "Logo") {
            params += "format=Webp"
            params += "quality=85"
        }
        if (params.isNotEmpty()) sb.append("?").append(params.joinToString("&"))
        return sb.toString()
    }

    /** Poster for any item, falling back to the series poster for episodes. */
    fun posterUrl(item: BaseItemDto, maxWidth: Int = 342): String? {
        val tag = item.ImageTags["Primary"]
        if (tag != null) return imageUrl(item.Id, "Primary", maxWidth, tag)
        val seriesTag = item.SeriesPrimaryImageTag
        val seriesId = item.SeriesId
        if (seriesTag != null && seriesId != null) return imageUrl(seriesId, "Primary", maxWidth, seriesTag)
        return null
    }

    /** Wide image for backdrop/thumb usage, with parent fallbacks. */
    fun backdropUrl(item: BaseItemDto, maxWidth: Int = 1280): String? {
        if (!item.BackdropImageTags.isNullOrEmpty()) return imageUrl(item.Id, "Backdrop", maxWidth, index = 0)
        if (item.ImageTags.containsKey("Thumb")) return imageUrl(item.Id, "Thumb", maxWidth, item.ImageTags["Thumb"])
        val parentId = item.ParentBackdropItemId
        if (parentId != null && !item.ParentBackdropImageTags.isNullOrEmpty()) {
            return imageUrl(parentId, "Backdrop", maxWidth, index = 0)
        }
        val seriesTag = item.SeriesPrimaryImageTag
        val seriesId = item.SeriesId
        if (seriesTag != null && seriesId != null) return imageUrl(seriesId, "Primary", maxWidth, seriesTag)
        return posterUrl(item, maxWidth)
    }

    /** Episode thumbnail. */
    fun thumbUrl(item: BaseItemDto, maxWidth: Int = 480): String? {
        val tag = item.ImageTags["Primary"]
        if (tag != null) return imageUrl(item.Id, "Primary", maxWidth, tag)
        return backdropUrl(item, maxWidth)
    }

    fun personImageUrl(person: BaseItemPerson, maxWidth: Int = 200): String? {
        val id = person.Id ?: return null
        val tag = person.PrimaryImageTag ?: return null
        return imageUrl(id, "Primary", maxWidth, tag)
    }

    /** Title logo. Episodes never carry their own Logo image, so theirs is the series logo. */
    fun logoUrl(item: BaseItemDto, maxWidth: Int = 600): String {
        val id = if (item.isEpisode && item.SeriesId != null) item.SeriesId else item.Id
        return imageUrl(id, "Logo", maxWidth)
    }
}
