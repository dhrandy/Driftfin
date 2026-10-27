package tv.jetflix.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import tv.jetflix.app.R

/** No clock, network work, focus target, or animation: only the current initial-load state matters. */
internal fun showStartupWall(loading: Boolean, hasContent: Boolean, initialLoadFinished: Boolean): Boolean =
    loading && !hasContent && !initialLoadFinished

/** Replaces the empty first Home composition, never login, playback or an already-visible Home refresh. */
@Composable
fun InitialHomeLoad(
    loading: Boolean,
    hasContent: Boolean,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    var initialLoadFinished by remember { mutableStateOf(false) }
    LaunchedEffect(loading, hasContent) {
        // Success, empty-library and error states all end the initial wall. No minimum display time.
        if (!loading || hasContent) initialLoadFinished = true
    }
    if (enabled && showStartupWall(loading, hasContent, initialLoadFinished)) StartupScreen() else content()
}

@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.startup_wall),
            contentDescription = "Driftfin is loading",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
