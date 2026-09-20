// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState
import designsystem.Spacing
import kotlinx.coroutines.delay
import player.PlayerUiState
import player.PlayerViewModel

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — not on a rotation, which destroys
 * and recreates this same composition too (there is no
 * `android:configChanges`) while the singleton player/ViewModel underneath
 * survive regardless; see [shouldStopOnDispose].
 *
 * A tap toggles the transport bar, which takes itself away while a film runs
 * and stays while it is paused; see [controlsShouldFade]. There is no seek
 * control yet — see [PlayerControls].
 */
@Composable
fun PlayerScreen(setId: String, onBack: () -> Unit) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(setId) { viewModel.open(setId) }
    DisposableEffect(Unit) {
        onDispose {
            if (shouldStopOnDispose(activity?.isChangingConfigurations == true)) {
                viewModel.stop()
            }
        }
    }
    KeepScreenOnWhile(isPlaying = state is PlayerUiState.Playing)

    // Shown when the screen opens, so a viewer finds out the bar is there at
    // all, then left to take itself away.
    var controlsShown by remember { mutableStateOf(true) }
    LaunchedEffect(controlsShown, state) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = state is PlayerUiState.Playing)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        controlsShown = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controlsShown = !controlsShown } },
        contentAlignment = Alignment.Center,
    ) {
        player?.let { current ->
            Video(current)
            if (controlsShown && controlsMayShow(state)) {
                PlayerControls(
                    player = current,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        when (state) {
            PlayerUiState.Preparing -> CenteredSpinner()
            is PlayerUiState.Failed -> CenteredError((state as PlayerUiState.Failed).message)
            PlayerUiState.Playing, PlayerUiState.Paused -> Unit
        }

        // Placed explicitly: the box centres its children so the picture
        // sits in the middle of its letterbox, and back would otherwise be
        // centred with it, in the middle of the film.
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.medium),
        ) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/**
 * The picture, shaped to itself rather than to the screen.
 *
 * `PlayerSurface` draws into whatever bounds it is given and applies no
 * ratio of its own — the old `PlayerView` had a frame layout that did — so
 * filling the window stretches a 2.4:1 film onto a 3:2 display and makes
 * everyone in it tall and thin. The black behind is the letterbox.
 *
 * The size comes from media3's own presentation state rather than from a
 * listener written here: it already folds in the pixel shape that makes
 * anamorphic video 2.4:1 rather than 1.78:1, and it already knows when the
 * surface is showing a frame that no longer belongs to what is playing.
 */
@Composable
private fun Video(player: Player) {
    val presentation = rememberPresentationState(player)
    val size = presentation.videoSizeDp
    val shaped = if (size != null && size.width > 0f && size.height > 0f) {
        Modifier.fillMaxSize().aspectRatio(size.width / size.height)
    } else {
        Modifier.fillMaxSize()
    }
    PlayerSurface(player = player, modifier = shaped)
    // Between one set and the next the surface still holds the last frame
    // of the old one. Covering it is what media3 asks callers to do, and
    // the alternative is a still from the previous film over the new one's
    // audio.
    if (presentation.coverSurface) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}

/**
 * A rotation disposes and recreates this screen's whole composition
 * exactly the way leaving it for the catalog does; the two are told apart
 * by whether the Activity itself is mid configuration change. Stopping on
 * a rotation would restart the same set from zero every time the device
 * turns, which is worse than the drop-to-catalog bug this replaced.
 */
internal fun shouldStopOnDispose(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations

/**
 * `LocalContext.current` is not necessarily the Activity itself — a
 * `ContextThemeWrapper` or a dialog host wraps it, and a naive
 * `as? Activity` cast would silently see neither and always stop. This
 * unwraps `ContextWrapper.baseContext` until it finds one, matching how
 * `MainActivity` (no such wrapper today) and any future one both resolve
 * correctly.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun KeepScreenOnWhile(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun CenteredError(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.large), contentAlignment = Alignment.Center) {
        Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
