// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import designsystem.Spacing
import player.PlayerUiState
import player.PlayerViewModel

/**
 * Hosts the shared [PlayerViewModel] behind a `PlayerSurface`, keeping the
 * screen awake while a set is actually playing and stopping playback when
 * this leaves composition for real — not on a rotation, which destroys
 * and recreates this same composition too (there is no
 * `android:configChanges`) while the singleton player/ViewModel underneath
 * survive regardless; see [shouldStopOnDispose]. Touch controls are
 * intentionally minimal: back, and a loading/error overlay. There is no
 * scrubber or seek bar yet, since nothing has verified seeking across a
 * part boundary works; adding one before that is proven would let a user
 * hit a bug no test caught.
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        player?.let { PlayerSurface(player = it, modifier = Modifier.fillMaxSize()) }

        when (state) {
            PlayerUiState.Preparing -> CenteredSpinner()
            is PlayerUiState.Failed -> CenteredError((state as PlayerUiState.Failed).message)
            is PlayerUiState.Playing, is PlayerUiState.Paused -> Unit
        }

        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.medium)) {
            Text(text = "←", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
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
