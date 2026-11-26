package tv.jetflix.phone.cast

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** The standard Google cast button for Compose screens. Renders only when the
 *  Cast framework is usable on this device; discovery and the device picker are
 *  handled by the framework itself.
 *
 *  The cast widgets are AppCompat-era widgets: the button needs a full AppCompat
 *  theme ancestor (Driftfin runs a platform Material theme, and mediarouter's own
 *  Theme.MediaRouter is only an overlay, which crashes button creation), and its
 *  device-picker dialog needs a FragmentActivity (MainActivity is one). A failure
 *  to build the button must degrade to no button, never crash the screen. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    if (!CastManager.available) return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            try {
                val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
                MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) }
            } catch (e: Exception) {
                CastManager.buttonBroken = true
                View(ctx)
            }
        }
    )
}
