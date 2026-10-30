package tv.jetflix.phone.data

import kotlinx.serialization.Serializable

/** A remote link Jellyfin attaches to an item, such as a trailer address. */
@Serializable
data class MediaUrl(val Name: String? = null, val Url: String? = null)
