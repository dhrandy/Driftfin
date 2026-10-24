package tv.jetflix.app.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class JellyfinRepository {

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private var api: JellyfinApi? = null
    var serverUrl: String? = null
        private set

    /** Normalize user input into a base server URL: add scheme, strip trailing slash and /web. */
    fun normalizeServerUrl(input: String): String {
        var url = input.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        url = url.trimEnd('/')
        if (url.endsWith("/web")) url = url.dropLast(4)
        return url
    }

    fun apiFor(baseUrl: String): JellyfinApi {
        val existing = api
        if (existing != null && serverUrl == baseUrl) return existing
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", SessionManager.authorizationHeader())
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

    fun logoUrl(itemId: String, maxWidth: Int = 600): String =
        imageUrl(itemId, "Logo", maxWidth)
}
