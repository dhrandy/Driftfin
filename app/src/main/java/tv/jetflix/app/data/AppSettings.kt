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
        _posterSize.value = PosterSize.values().firstOrNull { it.name == p.getString("poster_size", null) } ?: PosterSize.Medium
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; prefs?.edit()?.putBoolean("autoplay_next", v)?.apply() }
    fun setShowMyList(v: Boolean) { _showMyList.value = v; prefs?.edit()?.putBoolean("show_my_list", v)?.apply() }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; prefs?.edit()?.putBoolean("show_next_up", v)?.apply() }
}
