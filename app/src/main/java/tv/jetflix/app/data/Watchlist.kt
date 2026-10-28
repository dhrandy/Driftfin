package tv.jetflix.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Driftfin's own Watchlist: movies and shows starred to watch later, separate from Jellyfin
 * favorites (the heart). Mirrors Marquee's verified behavior (src/watch-later.js, src/server.js):
 * Marquee's own per-user list rather than a Jellyfin construct, newest starred first, 500-title
 * cap, movie/series only (enforced where the star is offered). Stored locally on the TV, keyed by
 * server + user so profiles and servers never see each other's list; nothing is sent to Jellyfin.
 */
object Watchlist {
    const val MAX = 500
    private var prefs: android.content.SharedPreferences? = null
    private var owner: String? = null
    private val _ids = MutableStateFlow<List<String>>(emptyList())

    /** Starred Jellyfin item ids for the signed-in user, newest first. */
    val ids: StateFlow<List<String>> = _ids

    private val _capNotice = MutableStateFlow(false)
    /** True after a star was refused because the list is full; the screen shows it, then clears it. */
    val capNotice: StateFlow<Boolean> = _capNotice
    fun clearCapNotice() { _capNotice.value = false }

    fun init(context: Context) {
        if (prefs == null) prefs = context.getSharedPreferences("jetflix_watchlist", Context.MODE_PRIVATE)
    }

    fun setUser(serverUrl: String?, userId: String?) {
        owner = if (!serverUrl.isNullOrBlank() && !userId.isNullOrBlank()) "$serverUrl#$userId" else null
        _ids.value = load()
    }

    private fun load(): List<String> {
        val o = owner ?: return emptyList()
        return prefs?.getString(o, null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun contains(id: String): Boolean = _ids.value.contains(id)

    /** Stars or unstars a title; returns the new starred state. An add past the 500 cap is refused. */
    @Synchronized
    fun toggle(id: String): Boolean {
        val o = owner ?: return false
        val current = _ids.value
        val next: List<String>
        val starred: Boolean
        if (current.contains(id)) {
            next = current - id
            starred = false
        } else {
            if (current.size >= MAX) { _capNotice.value = true; return false }
            next = listOf(id) + current
            starred = true
        }
        prefs?.edit()?.putString(o, next.joinToString(","))?.apply()
        _ids.value = next
        return starred
    }

    /** Drops ids the server no longer returns (deleted or lost access); keeps the order of the rest. */
    @Synchronized
    fun pruneMissing(found: Set<String>) {
        val o = owner ?: return
        val current = _ids.value
        val kept = current.filter { found.contains(it) }
        if (kept.size != current.size) {
            prefs?.edit()?.putString(o, kept.joinToString(","))?.apply()
            _ids.value = kept
        }
    }

    internal fun resetForTest() {
        prefs?.edit()?.clear()?.apply()
        owner = null
        _ids.value = emptyList()
        prefs = null
    }
}
