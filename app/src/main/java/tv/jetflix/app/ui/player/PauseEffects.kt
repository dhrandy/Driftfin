package tv.jetflix.app.ui.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.media3.ui.PlayerView

/** Optional effects on the video frame while paused: black-and-white and/or blur, each its own
 *  Settings toggle, both OFF by default (blur was a suspect in the TV scroll-stutter investigation,
 *  so these are strictly opt-in). RenderEffect needs Android 12 (API 31) and the video must render
 *  through a TextureView: a SurfaceView's video lives in a separate layer that view effects cannot
 *  reach. On older devices the state reports no effect and the player keeps its default surface. */
object PauseEffects {
    const val BLUR_RADIUS = 25f

    /** What the player should do for the given toggles on the given device. */
    data class State(val textureSurface: Boolean, val grayscale: Boolean, val blurRadius: Float)

    /** Both effects are RenderEffect-based, so both need Android 12 (API 31) and a TextureView.
     *  Same threshold for grayscale and blur - there is no lower-API grayscale path. */
    fun supported(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

    fun state(bwOn: Boolean, blurOn: Boolean, sdkInt: Int): State {
        val supported = supported(sdkInt)
        return State(
            textureSurface = supported && (bwOn || blurOn),
            grayscale = supported && bwOn,
            blurRadius = if (supported && blurOn) BLUR_RADIUS else 0f,
        )
    }

    /** The effect for the current pause state, or null for an untouched frame (playing, or all off). */
    fun renderEffect(s: State, paused: Boolean): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (!paused || (!s.grayscale && s.blurRadius <= 0f)) return null
        var fx: RenderEffect? = null
        if (s.blurRadius > 0f) {
            fx = RenderEffect.createBlurEffect(s.blurRadius, s.blurRadius, Shader.TileMode.CLAMP)
        }
        if (s.grayscale) {
            val gray = RenderEffect.createColorFilterEffect(
                ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }),
            )
            fx = if (fx != null) RenderEffect.createChainEffect(gray, fx) else gray
        }
        return fx
    }

    /** Applies (or clears) the pause effect on the player view. No-op below API 31. */
    fun applyTo(view: PlayerView, paused: Boolean, s: State) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.setRenderEffect(renderEffect(s, paused))
    }
}
