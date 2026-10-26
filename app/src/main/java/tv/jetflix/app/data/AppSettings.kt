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

    private val _skipIntro = MutableStateFlow(true)
    val skipIntro: StateFlow<Boolean> = _skipIntro
    fun setSkipIntro(v: Boolean) { _skipIntro.value = v; prefs?.edit()?.putBoolean("skip_intro", v)?.apply() }

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

    private var prefs: android.content.SharedPreferences? = null
    private val _autoplayNext = MutableStateFlow(true)
    private val _showMyList = MutableStateFlow(true)
    private val _showNextUp = MutableStateFlow(true)
    val autoplayNext: StateFlow<Boolean> = _autoplayNext
    val showMyList: StateFlow<Boolean> = _showMyList
    val showNextUp: StateFlow<Boolean> = _showNextUp

    fun init(context: Context) {
        val p = context.getSharedPreferences("jetflix_settings", Context.MODE_PRIVATE)
        prefs = p
        _autoplayNext.value = p.getBoolean("autoplay_next", true)
        _showMyList.value = p.getBoolean("show_my_list", true)
        _showNextUp.value = p.getBoolean("show_next_up", true)
        _liveBlur.value = p.getBoolean("live_blur", true)
        _continueWatchingPosters.value = p.getBoolean("continue_watching_posters", false)
        _skipIntro.value = p.getBoolean("skip_intro", true)
        _titleLogos.value = p.getBoolean("title_logos", true)
        _formatBadges.value = p.getBoolean("format_badges", true)
        _pauseCast.value = p.getBoolean("pause_cast", true)
        _buttonSound.value = tv.jetflix.app.ui.components.ButtonSound.values().firstOrNull { it.name == p.getString("button_sound", null) } ?: tv.jetflix.app.ui.components.ButtonSound.Off
        tv.jetflix.app.ui.components.ButtonSounds.style = _buttonSound.value
        _posterSize.value = PosterSize.values().firstOrNull { it.name == p.getString("poster_size", null) } ?: PosterSize.Medium
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; prefs?.edit()?.putBoolean("autoplay_next", v)?.apply() }
    fun setShowMyList(v: Boolean) { _showMyList.value = v; prefs?.edit()?.putBoolean("show_my_list", v)?.apply() }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; prefs?.edit()?.putBoolean("show_next_up", v)?.apply() }
}
