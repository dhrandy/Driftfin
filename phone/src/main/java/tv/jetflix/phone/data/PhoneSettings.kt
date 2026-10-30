package tv.jetflix.phone.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Poster grid density across Browse, Search and My Stuff. Medium is the original three columns. */
enum class PosterSize(val label: String, val gridColumns: Int) {
    Small("Small", 4), Medium("Medium", 3), Large("Large", 2);
    fun next() = values()[(ordinal + 1) % values().size]
}

/** Small on-phone options. Same defaults as the TV app. */
object PhoneSettings {
    private var prefs: android.content.SharedPreferences? = null
    private val _autoplayNext = MutableStateFlow(true)
    /** Starts the next episode after a 5 second countdown. */
    val autoplayNext: StateFlow<Boolean> = _autoplayNext

    private val _titleLogos = MutableStateFlow(true)
    /** Shows a title's own logo image on Home and Details when the server has one. */
    val titleLogos: StateFlow<Boolean> = _titleLogos

    private val _posterSize = MutableStateFlow(PosterSize.Medium)
    /** Poster grid density: Small, Medium or Large. */
    val posterSize: StateFlow<PosterSize> = _posterSize

    private val _formatBadges = MutableStateFlow(true)
    /** Shows the 4K badge on covers. */
    val formatBadges: StateFlow<Boolean> = _formatBadges

    private val _heroRatings = MutableStateFlow(true)
    /** Shows the content rating (PG-13 and friends) on the Home hero. */
    val heroRatings: StateFlow<Boolean> = _heroRatings

    private val _showNextUp = MutableStateFlow(true)
    /** The Next Up row on Home. */
    val showNextUp: StateFlow<Boolean> = _showNextUp

    private val _showFavorites = MutableStateFlow(true)
    /** The Favorites row on Home. */
    val showFavorites: StateFlow<Boolean> = _showFavorites

    private val _lastSeenBuild = MutableStateFlow(0)
    /** The newest build whose What's New card was dismissed. */
    val lastSeenBuild: StateFlow<Int> = _lastSeenBuild

    fun init(context: Context) {
        val p = context.getSharedPreferences("driftfin_phone_settings", Context.MODE_PRIVATE)
        prefs = p
        _autoplayNext.value = p.getBoolean("autoplay_next", true)
        _posterSize.value = p.getString("poster_size", null)?.let { runCatching { PosterSize.valueOf(it) }.getOrNull() } ?: PosterSize.Medium
        _formatBadges.value = p.getBoolean("format_badges", true)
        _heroRatings.value = p.getBoolean("hero_ratings", true)
        _showNextUp.value = p.getBoolean("show_next_up", true)
        _showFavorites.value = p.getBoolean("show_favorites", true)
        _titleLogos.value = p.getBoolean("title_logos", true)
        _lastSeenBuild.value = p.getInt("last_seen_build", 0)
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; prefs?.edit()?.putBoolean("autoplay_next", v)?.apply() }
    fun setTitleLogos(v: Boolean) { _titleLogos.value = v; prefs?.edit()?.putBoolean("title_logos", v)?.apply() }
    fun setLastSeenBuild(v: Int) { _lastSeenBuild.value = v; prefs?.edit()?.putInt("last_seen_build", v)?.apply() }
    fun setPosterSize(v: PosterSize) { _posterSize.value = v; prefs?.edit()?.putString("poster_size", v.name)?.apply() }
    fun setFormatBadges(v: Boolean) { _formatBadges.value = v; prefs?.edit()?.putBoolean("format_badges", v)?.apply() }
    fun setHeroRatings(v: Boolean) { _heroRatings.value = v; prefs?.edit()?.putBoolean("hero_ratings", v)?.apply() }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; prefs?.edit()?.putBoolean("show_next_up", v)?.apply() }
    fun setShowFavorites(v: Boolean) { _showFavorites.value = v; prefs?.edit()?.putBoolean("show_favorites", v)?.apply() }
}
