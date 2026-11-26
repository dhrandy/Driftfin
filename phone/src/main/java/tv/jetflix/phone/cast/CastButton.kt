package tv.jetflix.phone.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** The standard Google cast button for Compose screens. Renders only when the
 *  Cast framework is usable on this device; discovery and the device picker are
 *  handled by the framework itself. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    if (!CastManager.available) return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // MediaRouteButton needs an AppCompat-flavored theme ancestor.
            val themed = ContextThemeWrapper(ctx, androidx.mediarouter.R.style.Theme_MediaRouter)
            MediaRouteButton(themed).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) }
        }
    )
}
