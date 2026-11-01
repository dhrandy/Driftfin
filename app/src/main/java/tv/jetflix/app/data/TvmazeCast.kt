package tv.jetflix.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

// TVMaze episode-cast fallback (TV48): some seasons carry no Actor People on the Jellyfin
// server because the provider splits the season into its own entry (Jury Duty season 2 =
// TVMaze's separate "Jury Duty Presents: Company Retreat"). When the playing episode has
// no actors of its own, we resolve THAT season on TVMaze - never by provider id and never
// by name alone: a candidate show's season must match every Jellyfin episode on number +
// title + air date. Only then do we show the TVMaze guest cast of the exact episode being
// watched. No verified match, no fallback.
//
// TVMaze is a free public API: no account, no key (https://www.tvmaze.com/api; data
// CC BY-SA 4.0, credit + link shown in Settings). Rate limit is at least 20 calls per
// 10 seconds per IP; a pause needs at most ~3 calls, all cached for the session.
//
// Deliberately NOT used: /shows/{id}/cast returns SHOW-level regulars. Those are not the
// playing episode's credits, so they are never merged in as if they were.

interface TvmazeApi {
    @GET("search/shows")
    suspend fun searchShows(@Query("q") query: String): Response<List<TvmazeSearchHit>>

    @GET("shows/{showId}/episodes")
    suspend fun showEpisodes(@Path("showId") showId: Long): Response<List<TvmazeEpisodeRef>>

    @GET("episodes/{episodeId}/guestcast")
    suspend fun episodeGuestCast(@Path("episodeId") episodeId: Long): Response<List<TvmazeGuestCredit>>
}

@Serializable
data class TvmazeSearchHit(val show: TvmazeShowRef? = null)

@Serializable
data class TvmazeShowRef(val id: Long = 0, val name: String? = null)

@Serializable
data class TvmazeEpisodeRef(val id: Long = 0, val name: String? = null, val season: Int = 0, val number: Int = 0, val airdate: String? = null)

@Serializable
data class TvmazeGuestCredit(val person: TvmazePerson? = null, val character: TvmazeCharacter? = null)

@Serializable
data class TvmazePerson(val id: Long = 0, val name: String? = null, val image: TvmazeImage? = null)

@Serializable
data class TvmazeCharacter(val name: String? = null)

@Serializable
data class TvmazeImage(val medium: String? = null, val original: String? = null)

/** Resolves Jellyfin seasons to TVMaze seasons by episode fingerprint and serves per-episode guest cast. */
class TvmazeCastResolver private constructor(private val api: TvmazeApi) {

    private val seasonCache = HashMap<String, Pair<Long, List<TvmazeEpisodeRef>>?>() // jellyfin seriesId|seasonNumber -> (tvmazeShowId, season episodes) or null
    private val showEpisodesCache = HashMap<Long, List<TvmazeEpisodeRef>>() // tvmazeShowId -> all episodes
    private val creditsCache = HashMap<Long, List<BaseItemPerson>>() // tvmazeEpisodeId -> people

    /**
     * TVMaze guest cast for the exact playing episode, or null when no season can be verified.
     * [jellyfinEpisodes] are the whole season's episodes (IndexNumber, Name, PremiereDate).
     * Server-only extra items (behind-the-scenes rows numbered past the real season) simply
     * have no TVMaze counterpart: they never shift the verified number-based mapping and never
     * get TVMaze credits - they stay local-only.
     */
    suspend fun episodeCast(item: BaseItemDto, jellyfinEpisodes: List<BaseItemDto>): List<BaseItemPerson>? {
        val seriesId = item.SeriesId ?: return null
        val seasonNo = item.ParentIndexNumber ?: return null
        val episodeNo = item.IndexNumber ?: return null
        if (jellyfinEpisodes.isEmpty()) return null
        val cacheKey = "$seriesId|$seasonNo"
        val season = if (seasonCache.containsKey(cacheKey)) seasonCache[cacheKey] else resolveSeason(item.SeriesName, jellyfinEpisodes).also { seasonCache[cacheKey] = it }
        val (tvmazeShowId, seasonEpisodes) = season ?: return null
        // Target resolution is by absolute episode number inside the verified season. An extra
        // (E9+ extras on a real season) matches nothing, so no credits are fetched for it.
        val targets = seasonEpisodes.filter { it.number == episodeNo }
        if (targets.size != 1) return null
        val episodeId = targets[0].id
        creditsCache[episodeId]?.let { return it }
        val credits = call { api.episodeGuestCast(episodeId) } ?: return null
        // Guest cast deduped by TVMaze person id. Show-level regulars are never fetched.
        // A credit with a missing or non-positive person id is rejected before any identity is
        // built from it: the model's default 0 must never materialize a bogus "person 0" or
        // collapse two id-less credits into one.
        val seen = HashSet<Long>()
        val people = credits.filter {
            val person = it.person
            person != null && person.id > 0 && !person.name.isNullOrBlank() && seen.add(person.id)
        }.map {
            val person = it.person!!
            BaseItemPerson(
                Name = person.name, Id = null, Role = it.character?.name, Type = "Actor",
                PrimaryImageTag = null, RemoteImageUrl = person.image?.medium,
                ProviderIds = mapOf("Tvmaze" to person.id.toString()),
            )
        }
        if (people.isEmpty()) return null
        creditsCache[episodeId] = people
        return people
    }

    /** The one candidate (show, season) whose episodes map 1:1 onto the Jellyfin season, else null. */
    private suspend fun resolveSeason(seriesName: String?, jellyfinEpisodes: List<BaseItemDto>): Pair<Long, List<TvmazeEpisodeRef>>? {
        val query = seriesName?.takeIf { it.isNotBlank() } ?: return null
        val candidates = (call { api.searchShows(query) }.orEmpty()).mapNotNull { it.show }.distinctBy { it.id }.take(5)
        var match: Pair<Long, List<TvmazeEpisodeRef>>? = null
        for (candidate in candidates) {
            val episodes = showEpisodesCache[candidate.id]
                ?: call { api.showEpisodes(candidate.id) }?.also { showEpisodesCache[candidate.id] = it }
                ?: continue
            // EVERY season of the candidate is tried, not only the one carrying the server's own
            // season number: a provider can split one aired season into its own show entry whose
            // episodes it numbers from season 1. Identity comes from the
            // fingerprint - every episode matching on number + title + air date - never from the
            // season label, so a differently-numbered season cannot sneak through.
            for (seasonEpisodes in episodes.filter { it.number >= 1 }.groupBy { it.season }.values) {
                // Server may carry MORE items than the real season (extras); never fewer than the candidate.
                if (seasonEpisodes.isEmpty() || seasonEpisodes.size > jellyfinEpisodes.size) continue
                if (!fingerprintMatches(jellyfinEpisodes, seasonEpisodes)) continue
                if (match != null) return null // ambiguous: two candidate seasons both match - stay hidden
                match = candidate.id to seasonEpisodes
            }
        }
        return match
    }

    /** One bounded retry on HTTP 429 (rate-limited); everything else fails closed to null. */
    private suspend fun <T> call(block: suspend () -> Response<T>): T? {
        var resp = runCatching { block() }.getOrNull() ?: return null
        if (resp.code() == 429) {
            delay(1500)
            resp = runCatching { block() }.getOrNull() ?: return null
        }
        return if (resp.isSuccessful) resp.body() else null
    }

    companion object {
        const val BASE_URL = "https://api.tvmaze.com/"

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun apiFor(baseUrl: String): TvmazeApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", "Driftfin (github.com/dhrandy/Driftfin)").build())
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TvmazeApi::class.java)
        }

        /** The shared resolver when the feature is on; no key or account needed. One per process is fine. */
        @Volatile private var cached: TvmazeCastResolver? = null

        fun resolver(apiOverride: TvmazeApi? = null): TvmazeCastResolver? {
            if (!AppSettings.tvmazeEpisodeCast.value) return null
            if (apiOverride != null) return TvmazeCastResolver(apiOverride)
            if (cached == null) cached = TvmazeCastResolver(apiFor(BASE_URL))
            return cached
        }

        internal fun normalizeTitle(s: String?): String =
            // "&" and "and" are the same word in episode titles ("Mergers & Acquisitions" ==
            // "Mergers and Acquisitions"): normalize before stripping punctuation.
            s.orEmpty().lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

        /**
         * Strict, extras-aware fingerprint: EVERY TVMaze episode must map onto EXACTLY ONE server
         * episode with the same episode number, the same normalized title, and the same air
         * date (non-blank on both sides). Server items beyond that - extras like behind-the-scenes
         * rows - are tolerated: they match nothing and stay local-only. But any TVMaze episode left
         * unmatched (a middle disagreement, a missing date, a duplicate server row) fails the
         * whole season. A title-only coincidence can never pass. No match, no cast enrichment.
         */
        internal fun fingerprintMatches(jellyfinEpisodes: List<BaseItemDto>, tvmazeEpisodes: List<TvmazeEpisodeRef>): Boolean {
            if (jellyfinEpisodes.isEmpty() || tvmazeEpisodes.isEmpty()) return false
            if (tvmazeEpisodes.size > jellyfinEpisodes.size) return false
            for (t in tvmazeEpisodes) {
                val matches = jellyfinEpisodes.filter { j ->
                    j.IndexNumber != null && j.IndexNumber == t.number &&
                        normalizeTitle(j.Name) == normalizeTitle(t.name) &&
                        sameAirDate(j.PremiereDate, t.airdate)
                }
                if (matches.size != 1) return false
            }
            return true
        }

        internal fun sameAirDate(jellyfinPremiereDate: String?, tvmazeAirDate: String?): Boolean {
            val j = jellyfinPremiereDate?.take(10)
            val t = tvmazeAirDate?.take(10)
            return !j.isNullOrBlank() && !t.isNullOrBlank() && j == t
        }
    }
}
