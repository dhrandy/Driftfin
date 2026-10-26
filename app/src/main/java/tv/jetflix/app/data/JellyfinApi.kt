package tv.jetflix.app.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {

    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): Response<PublicSystemInfo>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body body: AuthenticateUserByName): Response<AuthenticationResult>

    /** Every genre and year in a library (not just the current filtered list), for the filter pickers. */
    @GET("Items/Filters")
    suspend fun getItemFilters(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
    ): Response<QueryFilters>

    @GET("UserViews")
    suspend fun getUserViews(@Query("userId") userId: String): Response<BaseItemDtoQueryResult>

    @GET("UserItems/Resume")
    suspend fun getResumeItems(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 16,
        @Query("mediaTypes") mediaTypes: String = "Video",
        @Query("fields") fields: String = "PrimaryImageAspectRatio,Overview,Genres",
    ): Response<BaseItemDtoQueryResult>

    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 16,
        @Query("enableResumable") enableResumable: Boolean = false,
        @Query("fields") fields: String = "Overview,Genres",
    ): Response<BaseItemDtoQueryResult>

    @GET("Items/Latest")
    suspend fun getLatest(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 16,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("fields") fields: String = "Overview,Genres",
    ): Response<List<BaseItemDto>>

    @GET("Items")
    suspend fun getItems(
        @Query("userId") userId: String,
        @Query("parentId") parentId: String? = null,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("excludeItemTypes") excludeItemTypes: String? = null,
        @Query("recursive") recursive: Boolean? = null,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("fields") fields: String? = null,
        @Query("filters") filters: String? = null,
        @Query("genres") genres: String? = null,
        @Query("years") years: String? = null,
        @Query("is4K") is4K: Boolean? = null,
        @Query("enableImages") enableImages: Boolean? = null,
        @Query("enableUserData") enableUserData: Boolean? = null,
    ): Response<BaseItemDtoQueryResult>

    @GET("Items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
    ): Response<BaseItemDto>

    @GET("Items/{itemId}/LocalTrailers")
    suspend fun getLocalTrailers(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
    ): Response<List<BaseItemDto>>

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("fields") fields: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<BaseItemDtoQueryResult>

    @GET("Items/{itemId}/Similar")
    suspend fun getSimilar(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 16,
        @Query("fields") fields: String? = "Genres",
    ): Response<BaseItemDtoQueryResult>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Body body: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    ): Response<PlaybackInfoResponse>

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body info: PlaybackStartInfo): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body info: PlaybackProgressInfo): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body info: PlaybackStopInfo): Response<Unit>

    @POST("UserFavoriteItems/{itemId}")
    suspend fun addFavorite(@Path("itemId") itemId: String, @Query("userId") userId: String): Response<UserItemDataDto>

    @DELETE("UserFavoriteItems/{itemId}")
    suspend fun removeFavorite(@Path("itemId") itemId: String, @Query("userId") userId: String): Response<UserItemDataDto>

    @GET("Users/Public")
    suspend fun getPublicUsers(): Response<List<PublicUser>>

    @POST("UserPlayedItems/{itemId}")
    suspend fun markPlayed(@Path("itemId") itemId: String, @Query("userId") userId: String): Response<UserItemDataDto>

    @DELETE("UserPlayedItems/{itemId}")
    suspend fun markUnplayed(@Path("itemId") itemId: String, @Query("userId") userId: String): Response<UserItemDataDto>
}

@Serializable
data class PlaybackStartInfo(
    val ItemId: String,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val PositionTicks: Long? = null,
    val IsPaused: Boolean = false,
    val IsMuted: Boolean = false,
    val CanSeek: Boolean = true,
    val PlayMethod: String = "DirectPlay",
)

@Serializable
data class PlaybackProgressInfo(
    val ItemId: String,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val PositionTicks: Long? = null,
    val IsPaused: Boolean = false,
    val IsMuted: Boolean = false,
    val CanSeek: Boolean = true,
    val PlayMethod: String = "DirectPlay",
)

@Serializable
data class PlaybackStopInfo(
    val ItemId: String,
    val MediaSourceId: String? = null,
    val PlaySessionId: String? = null,
    val PositionTicks: Long? = null,
    val Failed: Boolean = false,
)

@Serializable
data class QueryFilters(
    val Genres: List<String> = emptyList(),
    val Years: List<Int> = emptyList(),
)
