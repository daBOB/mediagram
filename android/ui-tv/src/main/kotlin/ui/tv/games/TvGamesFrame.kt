package ui.tv.games

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import designsystem.TvType
import games.GameNightUi
import games.GameNightViewModel
import kotlinx.coroutines.launch
import ui.common.player.KeepScreenOnWhile

/**
 * Game night as one full-screen frame, drawn outside the catalogue's chrome
 * like the player. Back first steps a running game back to the lobby; only
 * when there was nothing to step back from does it leave, and leaving stops
 * the host.
 */
@Composable
internal fun TvGamesFrame(leave: () -> Unit) {
    val model = hiltViewModel<GameNightViewModel>()
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.start() }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    KeepScreenOnWhile(isPlaying = true)
    // Nothing of a game night runs in the background: going away ends it, as Back from the lobby does.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLeave by rememberUpdatedState(leave)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latestLeave() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler { scope.launch { if (!model.back()) leave() } }

    when (val ui = state) {
        GameNightUi.Starting -> Centred("Starting game night…")
        is GameNightUi.Failed -> Centred("${ui.message}\n\nPress Back to return")
        is GameNightUi.Ready -> GamePage(ui.tvUrl)
    }
}

@Composable
private fun Centred(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = TvType.headlineSmall, textAlign = TextAlign.Center)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GamePage(url: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // AndroidView's default is WRAP_CONTENT, and a WebView then lays the page out at zero height.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // The remote's D-pad and OK reach the page only while this view holds focus.
                isFocusable = true
                isFocusableInTouchMode = true
                loadUrl(url)
            }
        },
        // After attach: asking from the factory runs before the view is in a window and does nothing.
        update = { view -> view.post { view.requestFocus() } },
        onRelease = { it.destroy() },
    )
}
