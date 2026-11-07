package ui.tv.games

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.common.player.KeepScreenOnWhile
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Game night as one full-screen frame, drawn outside the catalogue's chrome
 * like the player. It opens on the poster shelf while the host starts behind
 * it; a poster opens the lobby with that game set. Back steps a running game
 * back to the lobby, the lobby back to the shelf (the host keeps running, so
 * joined phones stay), and the shelf out, which stops the host.
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
    var chosen by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableIntStateOf(0) }
    // The page's settings panel closes on Back before anything else happens, so ask it first:
    // it answers "true" when it had a panel open and took the key. A page too busy to answer
    // (the board still building on the box) must not swallow Back, so after 400 ms it counts as "no".
    BackHandler {
        if (chosen == null) {
            leave()
            return@BackHandler
        }
        val stepBack = { scope.launch { if (!model.back()) chosen = null }; Unit }
        val web = page
        if (web == null) {
            stepBack()
            return@BackHandler
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

    val game = chosen
    if (game == null) {
        TvGamesShelf(focused, onFocus = { focused = it }) { picked ->
            // A host that failed to start gets another go with the next pick.
            if (state is GameNightUi.Failed) model.start()
            chosen = picked.kind
        }
        return
    }
    when (val ui = state) {
        GameNightUi.Starting -> Centred("Starting game night…")
        is GameNightUi.Failed -> Centred("${ui.message}\n\nPress Back to return")
        is GameNightUi.Ready -> GamePage("${ui.tvUrl}?game=$game") { page = it }
    }
}

@Composable
private fun Centred(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = TvType.headlineSmall, textAlign = TextAlign.Center)
    }
}

private const val AVATAR_PREFIX = "/game-avatars/"

/**
 * Answers the ceremony's avatar models from the APK's assets, so the 22 files (~26 MB) exist once in
 * the APK instead of once per ABI inside the Rust core with the web client. The assets folder is a
 * generated build output (make-ceremony-avatars.mjs); without it every request is a plain 404.
 * Everything else goes to the game host as before.
 */
private class AvatarAssetClient(private val assets: AssetManager) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val path = request.url.path ?: return null
        if (request.method != "GET" || !path.startsWith(AVATAR_PREFIX) || !path.endsWith(".glb")) return null
        val file = path.removePrefix(AVATAR_PREFIX)
        if (file.contains("/") || file.contains("..")) return notFound()
        return try {
            WebResourceResponse("model/gltf-binary", null, assets.open("game-avatars/$file"))
        } catch (_: IOException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
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
                webViewClient = AvatarAssetClient(context.applicationContext.assets)
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
