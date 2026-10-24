package tv.jetflix.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User-visible switches. Stored locally on the TV; nothing is sent to the server. */
object AppSettings {
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
    }

    fun setAutoplayNext(v: Boolean) { _autoplayNext.value = v; prefs?.edit()?.putBoolean("autoplay_next", v)?.apply() }
    fun setShowMyList(v: Boolean) { _showMyList.value = v; prefs?.edit()?.putBoolean("show_my_list", v)?.apply() }
    fun setShowNextUp(v: Boolean) { _showNextUp.value = v; prefs?.edit()?.putBoolean("show_next_up", v)?.apply() }
}
