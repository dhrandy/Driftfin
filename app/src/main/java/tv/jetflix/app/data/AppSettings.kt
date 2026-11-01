package tv.jetflix.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User-visible switches. Stored locally on the TV; nothing is sent to the server. */
/** Poster and card size across rows and grids. Medium is the original size. */
enum class PosterSize(val label: String, val scale: Float, val gridColumns: Int) {
    Small("Small", 0.85f, 7), Medium("Medium", 1f, 6), Large("Large", 1.25f, 5);
    fun next() = values()[(ordinal + 1) % values().size]
}

object AppSettings {
    private val _posterSize = MutableStateFlow(PosterSize.Medium)
    val posterSize: StateFlow<PosterSize> = _posterSize
    fun setPosterSize(v: PosterSize) { _posterSize.value = v; prefs?.edit()?.putString("poster_size", v.name)?.apply() }

    /** Live blur behind the menu and Home backdrop. On by default; off falls back to the small pre-scaled copy (a measurement switch). */
    private val _liveBlur = MutableStateFlow(true)
    val liveBlur: StateFlow<Boolean> = _liveBlur
    fun setLiveBlur(v: Boolean) { _liveBlur.value = v; prefs?.edit()?.putBoolean("live_blur", v)?.apply() }

    /** Continue Watching artwork: scene images (the original look) or posters. Scene images by default. */
    private val _continueWatchingPosters = MutableStateFlow(false)
    val continueWatchingPosters: StateFlow<Boolean> = _continueWatchingPosters
    fun setContinueWatchingPosters(v: Boolean) { _continueWatchingPosters.value = v; prefs?.edit()?.putBoolean("continue_watching_posters", v)?.apply() }

    /** Show a "Skip Intro" prompt while the server reports an Intro segment. On by default. */
    private val _titleLogos = MutableStateFlow(true)
    val titleLogos: StateFlow<Boolean> = _titleLogos
    fun setTitleLogos(v: Boolean) { _titleLogos.value = v; prefs?.edit()?.putBoolean("title_logos", v)?.apply() }

    /** HDR / Dolby Vision tags on movie Details. On by default. */
    private val _formatBadges = MutableStateFlow(true)
    val formatBadges: StateFlow<Boolean> = _formatBadges
    fun setFormatBadges(v: Boolean) { _formatBadges.value = v; prefs?.edit()?.putBoolean("format_badges", v)?.apply() }

    /** Cast strip while paused in the player. On by default. */
    private val _pauseCast = MutableStateFlow(true)
    val pauseCast: StateFlow<Boolean> = _pauseCast
    fun setPauseCast(v: Boolean) { _pauseCast.value = v; prefs?.edit()?.putBoolean("pause_cast", v)?.apply() }

    // TVMaze episode-cast enrichment (pause screen): merges the exact playing episode's
    // TVMaze guest cast into the pause cast after the season is verified episode-by-episode.
    // TVMaze is a free public API - no account, no key, nothing to set up. Default on.
    private val _tvmazeEpisodeCast = MutableStateFlow(true)
    val tvmazeEpisodeCast: StateFlow<Boolean> = _tvmazeEpisodeCast
    fun setTvmazeEpisodeCast(v: Boolean) { _tvmazeEpisodeCast.value = v; prefs?.edit()?.putBoolean("tvmaze_episode_cast", v)?.apply() }

    /** Episode/movie info (summary + Up next line) on the paused player. On by default. */
    private val _pauseInfo = MutableStateFlow(true)
    val pauseInfo: StateFlow<Boolean> = _pauseInfo
    fun setPauseInfo(v: Boolean) { _pauseInfo.value = v; prefs?.edit()?.putBoolean("pause_info", v)?.apply() }

    // Pause frame effects, both OFF by default (blur was a scroll-stutter suspect on the TV, so these
    // are strictly opt-in): black-and-white and blur apply to the video frame while paused only.
    private val _pauseBw = MutableStateFlow(false)
    val pauseBw: StateFlow<Boolean> = _pauseBw
    fun setPauseBw(v: Boolean) { _pauseBw.value = v; prefs?.edit()?.putBoolean("pause_bw", v)?.apply() }
    private val _pauseBlur = MutableStateFlow(false)
    val pauseBlur: StateFlow<Boolean> = _pauseBlur
    fun setPauseBlur(v: Boolean) { _pauseBlur.value = v; prefs?.edit()?.putBoolean("pause_blur", v)?.apply() }

    /** Startup/periodic update checks may surface the popup + red dot. Off hides them; the startup check
     *  still runs and the Settings row can always check manually (always-check-on-startup stands). */
    private val _updateNotices = MutableStateFlow(true)
    val updateNotices: StateFlow<Boolean> = _updateNotices
    fun setUpdateNotices(v: Boolean) { _updateNotices.value = v; prefs?.edit()?.putBoolean("update_notices", v)?.apply() }

    /** Remembered player track choice: audio language (default English) and subtitle language (null = off). */
    var audioLanguage: String
        get() = prefs?.getString("audio_language", null) ?: "eng"
        set(v) { prefs?.edit()?.putString("audio_language", v)?.apply() }
    var subtitleLanguage: String?
        get() = prefs?.getString("subtitle_language", null)
        set(v) { prefs?.edit()?.apply { if (v == null) remove("subtitle_language") else putString("subtitle_language", v) }?.apply() }

    private val _skipIntro = MutableStateFlow(true)
    val skipIntro: StateFlow<Boolean> = _skipIntro
    fun setSkipIntro(v: Boolean) { _skipIntro.value = v; prefs?.edit()?.putBoolean("skip_intro", v)?.apply() }


    /** Recommended movie and show rows on the Search page before anything is typed. On by default. */
    private val _searchRecommendations = MutableStateFlow(true)
    val searchRecommendations: StateFlow<Boolean> = _searchRecommendations
    fun setSearchRecommendations(v: Boolean) { _searchRecommendations.value = v; prefs?.edit()?.putBoolean("search_recommendations", v)?.apply() }

    // Avatar choices are per profile: the prefs key carries a short hash of serverUrl+userId
    // (same shape as Session's profileKey), so two profiles on this TV keep independent picks.
    private fun avatarScoped(base: String): String {
        val s = SessionManager.session.value ?: return base
        val h = java.security.MessageDigest.getInstance("SHA-256").digest("${s.serverUrl}${s.userId}".toByteArray())
        return base + "_" + h.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** Profile avatar picked by the active profile: 0 = none (the top bar shows the username as before). */
    private val _avatarId = MutableStateFlow(0)
    val avatarId: StateFlow<Int> = _avatarId
    fun setAvatarId(v: Int) { _avatarId.value = v; prefs?.edit()?.putInt(avatarScoped("avatar_id"), v)?.apply() }

    /** Local shown name for the active profile: overrides only what the top bar displays on this
     *  TV. Blank (the default) shows the real server username. Never sent to the server; sign-in,
     *  account and auth identity are untouched. */
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName
    fun setDisplayName(v: String) {
        val t = v.trim()
        _displayName.value = t
        prefs?.edit()?.apply { val k = avatarScoped("display_name"); if (t.isEmpty()) remove(k) else putString(k, t) }?.apply()
    }

    /** Top-bar profile display: username only (the original look, default), avatar + username, or avatar only. */
    private val _avatarDisplay = MutableStateFlow(AvatarDisplay.UsernameOnly)
    val avatarDisplay: StateFlow<AvatarDisplay> = _avatarDisplay
    fun setAvatarDisplay(v: AvatarDisplay) { _avatarDisplay.value = v; prefs?.edit()?.putString(avatarScoped("avatar_display"), v.name)?.apply() }

    private fun loadAvatars(p: android.content.SharedPreferences) {
        _avatarId.value = p.getInt(avatarScoped("avatar_id"), 0)
        _avatarDisplay.value = AvatarDisplay.values().firstOrNull { it.name == p.getString(avatarScoped("avatar_display"), null) } ?: AvatarDisplay.UsernameOnly
        _displayName.value = p.getString(avatarScoped("display_name"), null).orEmpty()
    }

    /** Rereads the avatar choice for the active profile. Called on sign-in, profile switch and sign-out. */
    fun reloadAvatars() { prefs?.let { loadAvatars(it) } }

    /** The featured-movie banner at the top of Home. On by default; off starts Home with the rows. */
    private val _homeBanner = MutableStateFlow(true)
    val homeBanner: StateFlow<Boolean> = _homeBanner
    fun setHomeBanner(v: Boolean) { _homeBanner.value = v; prefs?.edit()?.putBoolean("home_banner", v)?.apply() }

    /** Home banner only: exclude movies Jellyfin marks with the Animation genre. */
    private val _excludeHomeAnimation = MutableStateFlow(false)
    val excludeHomeAnimation: StateFlow<Boolean> = _excludeHomeAnimation
    fun setExcludeHomeAnimation(v: Boolean) { _excludeHomeAnimation.value = v; prefs?.edit()?.putBoolean("exclude_home_animation", v)?.apply() }

    /** Preview: age rating and community score beside More Info on the Home banner. On while previewed; hideable. */
    private val _heroRatings = MutableStateFlow(true)
    val heroRatings: StateFlow<Boolean> = _heroRatings
    fun setHeroRatings(v: Boolean) { _heroRatings.value = v; prefs?.edit()?.putBoolean("hero_ratings", v)?.apply() }

    /** Poster wall while Home does its first load. On by default; off shows the ordinary empty Home right away. */
    private val _startupWall = MutableStateFlow(true)
    val startupWall: StateFlow<Boolean> = _startupWall
    fun setStartupWall(v: Boolean) { _startupWall.value = v; prefs?.edit()?.putBoolean("startup_wall", v)?.apply() }

    private val _buttonSound = MutableStateFlow(tv.jetflix.app.ui.components.ButtonSound.Off)
    val buttonSound: StateFlow<tv.jetflix.app.ui.components.ButtonSound> = _buttonSound
    fun setButtonSound(v: tv.jetflix.app.ui.components.ButtonSound) {
        _buttonSound.value = v; tv.jetflix.app.ui.components.ButtonSounds.style = v
        prefs?.edit()?.putString("button_sound", v.name)?.apply()
    }

    /** Per-user, per-section Library choices (sort, filters) live in the same preferences file. */
    fun libraryPrefGet(key: String): String? = prefs?.getString(key, null)
    fun libraryPrefPut(key: String, value: String?) {
        prefs?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
    }

    /** Highest build number the "What's new" card was shown for. 0 = never shown. */
    var lastSeenBuild: Int
        get() = prefs?.getInt("last_seen_build", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("last_seen_build", v)?.apply() }

    private var prefs: android.content.SharedPreferences? = null
    private val _autoplayNext = MutableStateFlow(true)
    private val _showMyList = MutableStateFlow(true)
    private val _showNextUp = MutableStateFlow(true)
    private val _showWatchlist = MutableStateFlow(true)
    val autoplayNext: StateFlow<Boolean> = _autoplayNext
    val showMyList: StateFlow<Boolean> = _showMyList
    val showNextUp: StateFlow<Boolean> = _showNextUp
    /** Star button on movie/show Details and the Watchlist section under My Stuff. On by default. */
    val showWatchlist: StateFlow<Boolean> = _showWatchlist

    fun init(context: Context) {
        val p = context.getSharedPreferences("jetflix_settings", Context.MODE_PRIVATE)
        prefs = p
        _autoplayNext.value = p.getBoolean("autoplay_next", true)
        _showMyList.value = p.getBoolean("show_my_list", true)
        _showNextUp.value = p.getBoolean("show_next_up", true)
        _showWatchlist.value = p.getBoolean("show_watchlist", true)
        _liveBlur.value = p.getBoolean("live_blur", true)
        _continueWatchingPosters.value = p.getBoolean("continue_watching_posters", false)
        _skipIntro.value = p.getBoolean("skip_intro", true)
        _titleLogos.value = p.getBoolean("title_logos", true)
        _formatBadges.value = p.getBoolean("format_badges", true)
        _pauseCast.value = p.getBoolean("pause_cast", true)
        _tvmazeEpisodeCast.value = p.getBoolean("tvmaze_episode_cast", true)
        _pauseInfo.value = p.getBoolean("pause_info", true)
        _pauseBw.value = p.getBoolean("pause_bw", false)
        _pauseBlur.value = p.getBoolean("pause_blur", false)
        _updateNotices.value = p.getBoolean("update_notices", true)
        _startupWall.value = p.getBoolean("startup_wall", true)
        _homeBanner.value = p.getBoolean("home_banner", true)
        _excludeHomeAnimation.value = p.getBoolean("exclude_home_animation", false)
        _heroRatings.value = p.getBoolean("hero_ratings", true)
        _searchRecommendations.value = p.getBoolean("search_recommendations", true)
        loadAvatars(p)
        _buttonSound.value = tv.jetflix.app.ui.components.ButtonSound.values().firstOrNull { it.name == p.getString("button_sound", null) } ?: tv.jetflix.app.ui.components.ButtonSound.Off
        tv.jetflix.app.ui.components.ButtonSounds.style = _buttonSound.value
        _posterSize.value = PosterSize.values().firstOrNull { it.name == p.getString("poster_size", null) } ?: PosterSize.Medium
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; prefs?.edit()?.putBoolean("autoplay_next", v)?.apply() }
    fun setShowMyList(v: Boolean) { _showMyList.value = v; prefs?.edit()?.putBoolean("show_my_list", v)?.apply() }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; prefs?.edit()?.putBoolean("show_next_up", v)?.apply() }
    fun setShowWatchlist(v: Boolean) { _showWatchlist.value = v; prefs?.edit()?.putBoolean("show_watchlist", v)?.apply() }
}
