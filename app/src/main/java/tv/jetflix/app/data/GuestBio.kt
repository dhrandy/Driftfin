package tv.jetflix.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

// Guest biography without any key and without ever guessing identity. TVMaze people carry no
// biography and no external ids, but Wikidata holds a "TV Maze person ID" statement (P11449) on
// many people. The chain is id-keyed end to end: the TVMaze person id from the VERIFIED episode's
// guest cast -> Wikidata item with that exact P11449 statement (exactly one, or no bio) -> its
// English Wikipedia sitelink -> the Wikipedia page summary. No step matches a person by name, so a
// homonym can never supply the text. Any failed step simply yields no bio: the guest panel then
// shows only what the verified episode already proved (name, character, episode).
//
// Both services are free and keyless. Wikipedia text is CC BY-SA; the panel credits Wikipedia
// beside the TVMaze credit whenever a bio is shown.

interface WikidataApi {
    @GET("w/api.php?action=query&list=search&format=json&srlimit=5")
    suspend fun searchStatement(@Query("srsearch") srsearch: String): Response<WikidataSearchResponse>

    @GET("w/api.php?action=wbgetentities&props=sitelinks&sitefilter=enwiki&format=json")
    suspend fun entities(@Query("ids") ids: String): Response<WikidataEntitiesResponse>
}

interface WikipediaApi {
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun summary(@Path("title") title: String): Response<WikipediaSummary>
}

@Serializable
data class WikidataSearchResponse(val query: WikidataSearchQuery? = null)

@Serializable
data class WikidataSearchQuery(val search: List<WikidataSearchItem> = emptyList())

@Serializable
data class WikidataSearchItem(val title: String? = null)

@Serializable
data class WikidataEntitiesResponse(val entities: Map<String, WikidataEntity> = emptyMap())

@Serializable
data class WikidataEntity(val sitelinks: Map<String, WikidataSitelink> = emptyMap())

@Serializable
data class WikidataSitelink(val title: String? = null)

@Serializable
data class WikipediaSummary(val title: String? = null, val extract: String? = null)

/** One-shot biography for a TVMaze person, or null when the id chain cannot prove one. */
class GuestBioResolver internal constructor(private val wikidata: WikidataApi, private val wikipedia: WikipediaApi) {

    // Only successful biographies are cached: a transient failure (429, timeout, offline) must not
    // suppress a person's bio for the process lifetime - the next open simply tries again.
    private val cache = HashMap<String, String>() // tvmaze person id -> bio

    suspend fun bioFor(tvmazePersonId: String): String? {
        cache[tvmazePersonId]?.let { return it }
        val bio = resolve(tvmazePersonId)
        if (bio != null) cache[tvmazePersonId] = bio
        return bio
    }

    private suspend fun resolve(tvmazePersonId: String): String? {
        val id = tvmazePersonId.trim().takeIf { it.all { c -> c.isDigit() } } ?: return null
        // The item carrying exactly this TV Maze person ID statement. Zero or several = no bio.
        val hits = call { wikidata.searchStatement("haswbstatement:P11449=$id") }
            ?.query?.search.orEmpty().mapNotNull { it.title?.takeIf { t -> t.startsWith("Q") } }
        if (hits.size != 1) return null
        val title = call { wikidata.entities(hits[0]) }
            ?.entities?.get(hits[0])?.sitelinks?.get("enwiki")?.title?.takeIf { it.isNotBlank() } ?: return null
        return call { wikipedia.summary(title) }?.extract?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): T? {
        // Cancellation (the viewer opened a different person) is not a failure and is never cached.
        val resp = try { block() } catch (ce: CancellationException) { throw ce } catch (_: Exception) { return null }
        return if (resp.isSuccessful) resp.body() else null
    }

    companion object {
        const val WIKIDATA_URL = "https://www.wikidata.org/"
        const val WIKIPEDIA_URL = "https://en.wikipedia.org/"

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }

        private fun client() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "Driftfin (github.com/dhrandy/Driftfin)").build())
            }
            .build()

        fun wikidataFor(baseUrl: String): WikidataApi = Retrofit.Builder()
            .baseUrl(baseUrl).client(client())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(WikidataApi::class.java)

        fun wikipediaFor(baseUrl: String): WikipediaApi = Retrofit.Builder()
            .baseUrl(baseUrl).client(client())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(WikipediaApi::class.java)

        @Volatile private var cached: GuestBioResolver? = null

        fun resolver(override: GuestBioResolver? = null): GuestBioResolver =
            override ?: cached ?: GuestBioResolver(wikidataFor(WIKIDATA_URL), wikipediaFor(WIKIPEDIA_URL)).also { cached = it }
    }
}
