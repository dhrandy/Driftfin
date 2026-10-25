package tv.jetflix.app.data

import kotlinx.serialization.Serializable

@Serializable
data class MediaUrl(val Name: String? = null, val Url: String? = null)

/** What the Trailer button plays: a trailer file on the Jellyfin server, or a YouTube video through YouTube's embedded player. */
sealed interface TrailerSource {
    data class Local(val item: BaseItemDto) : TrailerSource
    data class YouTube(val videoId: String, val title: String) : TrailerSource
}

object Trailers {
    private val idRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com")

    /** The 11-character video id for the common YouTube link shapes, or null for anything else (other sites, playlists, channels). */
    fun youtubeVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val u = try { java.net.URI(url.trim()) } catch (_: Exception) { return null }
        if (u.scheme != "https" && u.scheme != "http") return null
        val host = u.host?.lowercase() ?: return null
        val id: String? = when {
            host == "youtu.be" -> u.path?.trim('/')?.substringBefore('/')
            host in hosts -> {
                val path = u.path.orEmpty()
                when {
                    path == "/watch" -> u.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
                    path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
                    path.startsWith("/v/") -> path.removePrefix("/v/").substringBefore('/')
                    else -> null
                }
            }
            else -> null
        }
        return id?.takeIf { idRegex.matches(it) }
    }

    /**
     * Server trailer files come first, like Jellyfin Web. Some servers count remote trailers in LocalTrailerCount,
     * so an empty local list falls through to the remote links.
     */
    fun pick(item: BaseItemDto, local: List<BaseItemDto>): TrailerSource? {
        local.firstOrNull { it.Id.isNotBlank() }?.let { return TrailerSource.Local(it) }
        for (r in item.RemoteTrailers) {
            val id = youtubeVideoId(r.Url) ?: continue
            return TrailerSource.YouTube(id, r.Name?.takeIf { it.isNotBlank() } ?: "${item.displayTitle()} trailer")
        }
        return null
    }
}
