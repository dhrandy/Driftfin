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

/** Playback quality cap. Auto (the default) starts uncapped and steps down on sustained buffering;
 *  a fixed choice pins the cap for every playback and never steps. capBps null = uncapped. */
enum class VideoQuality(val label: String, val capBps: Int?) {
    Auto("Auto", null),
    P1080("1080p - 10 Mbps", 10_000_000),
    P720("720p - 4 Mbps", 4_000_000),
    P480("480p - 2 Mbps", 2_000_000);
    fun next() = values()[(ordinal + 1) % values().size]
}

object AppSettings {
    private val _posterSize = MutableStateFlow(PosterSize.Medium)
    val posterSize: StateFlow<PosterSize> = _posterSize
    fun setPosterSize(v: PosterSize) { _posterSize.value = v; writeStr("poster_size", v.name) }

    /** Video quality cap (Auto default). A fixed pick applies the next time something plays. */
    private val _quality = MutableStateFlow(VideoQuality.Auto)
    val quality: StateFlow<VideoQuality> = _quality
    fun setQuality(v: VideoQuality) { _quality.value = v; writeStr("video_quality", v.name) }

    /** Live blur behind the menu and Home backdrop. On by default; off falls back to the small pre-scaled copy (a measurement switch). */
    private val _liveBlur = MutableStateFlow(true)
    val liveBlur: StateFlow<Boolean> = _liveBlur
    fun setLiveBlur(v: Boolean) { _liveBlur.value = v; writeBool("live_blur", v) }

    /** Continue Watching artwork: scene images (the original look) or posters. Scene images by default. */
    private val _continueWatchingPosters = MutableStateFlow(false)
    val continueWatchingPosters: StateFlow<Boolean> = _continueWatchingPosters
    fun setContinueWatchingPosters(v: Boolean) { _continueWatchingPosters.value = v; writeBool("continue_watching_posters", v) }

    /** Show a "Skip Intro" prompt while the server reports an Intro segment. On by default. */
    private val _titleLogos = MutableStateFlow(true)
    val titleLogos: StateFlow<Boolean> = _titleLogos
    fun setTitleLogos(v: Boolean) { _titleLogos.value = v; writeBool("title_logos", v) }

    /** HDR / Dolby Vision tags on movie Details. On by default. */
    private val _formatBadges = MutableStateFlow(true)
    val formatBadges: StateFlow<Boolean> = _formatBadges
    fun setFormatBadges(v: Boolean) { _formatBadges.value = v; writeBool("format_badges", v) }

    /** Cast strip while paused in the player. On by default. */
    private val _pauseCast = MutableStateFlow(true)
    val pauseCast: StateFlow<Boolean> = _pauseCast
    fun setPauseCast(v: Boolean) { _pauseCast.value = v; writeBool("pause_cast", v) }

    // TVMaze episode-cast enrichment (pause screen): merges the exact playing episode's
    // TVMaze guest cast into the pause cast after the season is verified episode-by-episode.
    // TVMaze is a free public API - no account, no key, nothing to set up. Default on.
    private val _tvmazeEpisodeCast = MutableStateFlow(true)
    val tvmazeEpisodeCast: StateFlow<Boolean> = _tvmazeEpisodeCast
    fun setTvmazeEpisodeCast(v: Boolean) { _tvmazeEpisodeCast.value = v; writeBool("tvmaze_episode_cast", v) }

    /** Episode/movie info (summary + Up next line) on the paused player. On by default. */
    private val _pauseInfo = MutableStateFlow(true)
    val pauseInfo: StateFlow<Boolean> = _pauseInfo
    fun setPauseInfo(v: Boolean) { _pauseInfo.value = v; writeBool("pause_info", v) }

    // Pause frame effects, both OFF by default (blur was a scroll-stutter suspect on the TV, so these
    // are strictly opt-in): black-and-white and blur apply to the video frame while paused only.
    private val _pauseBw = MutableStateFlow(false)
    val pauseBw: StateFlow<Boolean> = _pauseBw
    fun setPauseBw(v: Boolean) { _pauseBw.value = v; writeBool("pause_bw", v) }
    private val _pauseBlur = MutableStateFlow(false)
    val pauseBlur: StateFlow<Boolean> = _pauseBlur
    fun setPauseBlur(v: Boolean) { _pauseBlur.value = v; writeBool("pause_blur", v) }

    /** Startup/periodic update checks may surface the popup + red dot. Off hides them; the startup check
     *  still runs and the Settings row can always check manually (always-check-on-startup stands). */
    private val _updateNotices = MutableStateFlow(true)
    val updateNotices: StateFlow<Boolean> = _updateNotices
    fun setUpdateNotices(v: Boolean) { _updateNotices.value = v; writeBool("update_notices", v) }

    /** Remembered player track choice: audio language (default English) and subtitle language (null = off). */
    var audioLanguage: String
        get() = prefs?.let { readStr(it, "audio_language") } ?: "eng"
        set(v) { writeStr("audio_language", v) }
    var subtitleLanguage: String?
        get() = prefs?.let { readStr(it, "subtitle_language")?.takeIf { v -> v.isNotEmpty() } }
        // Off is stored as "" in the profile's own key: removing the key would fall back to the
        // shared legacy value and switch subtitles back on for this profile.
        set(v) { writeStr("subtitle_language", v ?: "") }

    private val _skipIntro = MutableStateFlow(true)
    val skipIntro: StateFlow<Boolean> = _skipIntro
    fun setSkipIntro(v: Boolean) { _skipIntro.value = v; writeBool("skip_intro", v) }


    /** Recommended movie and show rows on the Search page before anything is typed. On by default. */
    private val _homeAutoRefresh = MutableStateFlow(true)
    /** Home re-reads Continue Watching and Next Up on its own (on return to the app and about once a minute). On by default. */
    val homeAutoRefresh: StateFlow<Boolean> = _homeAutoRefresh
    fun setHomeAutoRefresh(v: Boolean) { _homeAutoRefresh.value = v; writeBool("home_auto_refresh", v) }

    private val _searchRecommendations = MutableStateFlow(true)
    val searchRecommendations: StateFlow<Boolean> = _searchRecommendations
    fun setSearchRecommendations(v: Boolean) { _searchRecommendations.value = v; writeBool("search_recommendations", v) }

    // Settings are per profile: the prefs key carries a short hash of serverUrl+userId, so two
    // profiles on this TV keep independent choices. Signed out there is no profile, so reads and
    // writes fall back to the plain shared key. Keys made before per-profile settings existed are
    // plain shared keys; a profile that never changed a setting still reads those, so existing
    // setups carry over unchanged (see ProfileScope).
    private fun profileScoped(base: String): String {
        val s = SessionManager.session.value ?: return base
        return ProfileScope.key(base, s.serverUrl, s.userId)
    }

    private fun readBool(p: android.content.SharedPreferences, base: String, def: Boolean): Boolean =
        p.getBoolean(ProfileScope.readKey(base, profileScoped(base), p::contains), def)

    private fun readStr(p: android.content.SharedPreferences, base: String): String? =
        p.getString(ProfileScope.readKey(base, profileScoped(base), p::contains), null)

    private fun writeBool(base: String, v: Boolean) { prefs?.edit()?.putBoolean(profileScoped(base), v)?.apply() }

    private fun writeStr(base: String, v: String) { prefs?.edit()?.putString(profileScoped(base), v)?.apply() }

    /** Profile avatar picked by the active profile: 0 = none (the top bar shows the username as before). */
    private val _avatarId = MutableStateFlow(0)
    val avatarId: StateFlow<Int> = _avatarId
    fun setAvatarId(v: Int) { _avatarId.value = v; prefs?.edit()?.putInt(profileScoped("avatar_id"), v)?.apply() }

    /** Local shown name for the active profile: overrides only what the top bar displays on this
     *  TV. Blank (the default) shows the real server username. Never sent to the server; sign-in,
     *  account and auth identity are untouched. */
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName
    fun setDisplayName(v: String) {
        val t = v.trim()
        _displayName.value = t
        prefs?.edit()?.apply { val k = profileScoped("display_name"); if (t.isEmpty()) remove(k) else putString(k, t) }?.apply()
    }

    /** Top-bar profile display: username only (the original look, default), avatar + username, or avatar only. */
    private val _avatarDisplay = MutableStateFlow(AvatarDisplay.UsernameOnly)
    val avatarDisplay: StateFlow<AvatarDisplay> = _avatarDisplay
    fun setAvatarDisplay(v: AvatarDisplay) { _avatarDisplay.value = v; prefs?.edit()?.putString(profileScoped("avatar_display"), v.name)?.apply() }

    private fun loadAvatars(p: android.content.SharedPreferences) {
        _avatarId.value = p.getInt(profileScoped("avatar_id"), 0)
        _avatarDisplay.value = AvatarDisplay.values().firstOrNull { it.name == p.getString(profileScoped("avatar_display"), null) } ?: AvatarDisplay.UsernameOnly
        _displayName.value = p.getString(profileScoped("display_name"), null).orEmpty()
    }

    /** Rereads the avatar choice for the active profile. Called on sign-in, profile switch and sign-out. */
    fun reloadAvatars() { prefs?.let { loadAvatars(it) } }

    /** The featured-movie banner at the top of Home. On by default; off starts Home with the rows. */
    private val _homeBanner = MutableStateFlow(true)
    val homeBanner: StateFlow<Boolean> = _homeBanner
    fun setHomeBanner(v: Boolean) { _homeBanner.value = v; writeBool("home_banner", v) }

    /** Home banner only: exclude movies Jellyfin marks with the Animation genre. */
    private val _excludeHomeAnimation = MutableStateFlow(false)
    val excludeHomeAnimation: StateFlow<Boolean> = _excludeHomeAnimation
    fun setExcludeHomeAnimation(v: Boolean) { _excludeHomeAnimation.value = v; writeBool("exclude_home_animation", v) }

    /** Preview: age rating and community score beside More Info on the Home banner. On while previewed; hideable. */
    private val _heroRatings = MutableStateFlow(true)
    val heroRatings: StateFlow<Boolean> = _heroRatings
    fun setHeroRatings(v: Boolean) { _heroRatings.value = v; writeBool("hero_ratings", v) }

    /** Poster wall while Home does its first load. On by default; off shows the ordinary empty Home right away. */
    private val _startupWall = MutableStateFlow(true)
    val startupWall: StateFlow<Boolean> = _startupWall
    fun setStartupWall(v: Boolean) { _startupWall.value = v; writeBool("startup_wall", v) }

    private val _buttonSound = MutableStateFlow(tv.jetflix.app.ui.components.ButtonSound.Off)
    val buttonSound: StateFlow<tv.jetflix.app.ui.components.ButtonSound> = _buttonSound
    fun setButtonSound(v: tv.jetflix.app.ui.components.ButtonSound) {
        _buttonSound.value = v; tv.jetflix.app.ui.components.ButtonSounds.style = v
        writeStr("button_sound", v.name)
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
        prefs = context.getSharedPreferences("jetflix_settings", Context.MODE_PRIVATE)
        reloadProfileSettings()
    }

    /** Rereads every per-profile setting for the now-active profile (or the shared values when
     *  signed out). Called at app start, on sign-in, on profile switch and on sign-out. */
    fun reloadProfileSettings() {
        val p = prefs ?: return
        _autoplayNext.value = readBool(p, "autoplay_next", true)
        _showMyList.value = readBool(p, "show_my_list", true)
        _showNextUp.value = readBool(p, "show_next_up", true)
        _showWatchlist.value = readBool(p, "show_watchlist", true)
        _liveBlur.value = readBool(p, "live_blur", true)
        _continueWatchingPosters.value = readBool(p, "continue_watching_posters", false)
        _skipIntro.value = readBool(p, "skip_intro", true)
        _titleLogos.value = readBool(p, "title_logos", true)
        _formatBadges.value = readBool(p, "format_badges", true)
        _pauseCast.value = readBool(p, "pause_cast", true)
        _tvmazeEpisodeCast.value = readBool(p, "tvmaze_episode_cast", true)
        _pauseInfo.value = readBool(p, "pause_info", true)
        _pauseBw.value = readBool(p, "pause_bw", false)
        _pauseBlur.value = readBool(p, "pause_blur", false)
        _updateNotices.value = readBool(p, "update_notices", true)
        _startupWall.value = readBool(p, "startup_wall", true)
        _homeBanner.value = readBool(p, "home_banner", true)
        _excludeHomeAnimation.value = readBool(p, "exclude_home_animation", false)
        _heroRatings.value = readBool(p, "hero_ratings", true)
        _searchRecommendations.value = readBool(p, "search_recommendations", true)
        _homeAutoRefresh.value = readBool(p, "home_auto_refresh", true)
        loadAvatars(p)
        _buttonSound.value = tv.jetflix.app.ui.components.ButtonSound.values().firstOrNull { it.name == readStr(p, "button_sound") } ?: tv.jetflix.app.ui.components.ButtonSound.Off
        tv.jetflix.app.ui.components.ButtonSounds.style = _buttonSound.value
        _posterSize.value = PosterSize.values().firstOrNull { it.name == readStr(p, "poster_size") } ?: PosterSize.Medium
        _quality.value = VideoQuality.values().firstOrNull { it.name == readStr(p, "video_quality") } ?: VideoQuality.Auto
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; writeBool("autoplay_next", v) }
    fun setShowMyList(v: Boolean) { _showMyList.value = v; writeBool("show_my_list", v) }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; writeBool("show_next_up", v) }
    fun setShowWatchlist(v: Boolean) { _showWatchlist.value = v; writeBool("show_watchlist", v) }
}
