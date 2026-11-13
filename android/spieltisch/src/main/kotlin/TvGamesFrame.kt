package com.mediagram.spieltisch

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.TvType
import designsystem.tvDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Game night as one full-screen frame: the host's TV page, which is the games shelf and the lobby in
 * one, opens as soon as the host runs. Back goes to the page first (its settings panel), then steps a
 * running game back to that page; on it, Back asks once and a second Back within [LEAVE_WINDOW_MS]
 * leaves through [leave], which stops the host and drops every phone at the table.
 */
@Composable
internal fun TvGamesFrame(
    model: GameNightViewModel,
    leave: () -> Unit,
) {
    val state by model.state.collectAsState()
    val filmsUnavailable by model.filmsUnavailable.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { model.start() }
    DisposableEffect(Unit) { onDispose { model.stop() } }
    // Nothing of a game night runs in the background: going away ends it, as Back from the shelf does.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLeave by rememberUpdatedState(leave)
    DisposableEffect(lifecycle) {
        // Stopped here, not only on dispose: Compose pauses its frames once the app is stopped, so the dispose may run only on return.
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    model.stop()
                    latestLeave()
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var page by remember { mutableStateOf<WebView?>(null) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        delay(LEAVE_WINDOW_MS)
        leaving = false
    }
    // The page's settings panel closes on Back before anything else happens, so ask it first:
    // it answers "true" when it had a panel open and took the key. A page too busy to answer
    // (the board still building on the box) must not swallow Back, so after 400 ms it counts as "no".
    BackHandler {
        val web = page
        if (web == null) {
            leave()
            return@BackHandler
        }
        val stepBack = {
            scope.launch {
                if (model.back()) return@launch
                if (leaving) leave() else leaving = true
            }
            Unit
        }
        var answered = false
        val giveUp = scope.launch {
            delay(400)
            answered = true
            stepBack()
        }
        web.evaluateJavascript("window.tvBack ? String(window.tvBack()) : 'false'") { answer ->
            if (answered) return@evaluateJavascript
            answered = true
            giveUp.cancel()
            if (answer?.trim('"') != "true") stepBack()
        }
    }

    when (val ui = state) {
        GameNightUi.Starting -> Centred("Starting game night…")
        is GameNightUi.Failed -> Failed(ui.message) { model.start() }
        // The page says so when the media app has not answered: its film games then have nothing to show.
        is GameNightUi.Ready ->
            Box(Modifier.fillMaxSize()) {
                GamePage(if (filmsUnavailable) "${ui.tvUrl}?films=0" else ui.tvUrl) { page = it }
                if (leaving) LeaveHint(Modifier.align(Alignment.BottomCenter))
            }
    }
}

/** How long the first Back on the shelf waits for a second one that ends the night. */
internal const val LEAVE_WINDOW_MS = 3_000L

/** A Back on the shelf ends the night for everyone, so the first one only says so. */
@Composable
private fun LeaveHint(modifier: Modifier) {
    Text(
        text = "Press Back again to end the game night",
        style = TvType.bodyMedium,
        color = Color.White,
        modifier = modifier.padding(bottom = 32.tvDp).background(Color(0xE60A4433), RoundedCornerShape(12.tvDp)).padding(horizontal = 20.tvDp, vertical = 10.tvDp),
    )
}

/** The host could not start: OK tries again, Back leaves. */
@Composable
private fun Failed(
    message: String,
    retry: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message, style = TvType.headlineSmall, textAlign = TextAlign.Center)
        Button(onClick = retry, modifier = Modifier.padding(top = 24.tvDp).focusRequester(focus)) { Text("Try again") }
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
private fun GamePage(url: String, onPage: (WebView?) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // AndroidView's default is WRAP_CONTENT, and a WebView then lays the page out at zero height.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Nobody taps a TV: without this the board's music and sounds would wait for a gesture forever.
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = GamePageClient(context.applicationContext.assets)
                // The remote's D-pad and OK reach the page only while this view holds focus.
                isFocusable = true
                isFocusableInTouchMode = true
                loadUrl(url)
                onPage(this)
            }
        },
        // After attach: asking from the factory runs before the view is in a window and does nothing.
        update = { view -> view.post { view.requestFocus() } },
        onRelease = {
            onPage(null)
            it.destroy()
        },
    )
}
