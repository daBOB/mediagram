package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import player.CONTROLS_LINGER_MS
import player.PlayerViewModel
import player.controlsShouldFade

/**
 * The side effects [PlayerScreen] runs for its own lifecycle rather than for
 * anything on screen — split out to keep that file under the project's line
 * guideline. The shared [PlayerLifecycle] (stop when left for real, save on
 * `ON_STOP`), plus what only the phone does: keep the screen awake while
 * [isPlaying], and hide the system bars for as long as this screen holds
 * them — see [ImmersiveEffect].
 */
@Composable
internal fun PlayerLifecycleEffects(viewModel: PlayerViewModel, isPlaying: Boolean) {
    PlayerLifecycle(viewModel)
    KeepScreenOnWhile(isPlaying = isPlaying)
    ImmersiveEffect()
}

/**
 * Takes the transport bar away after [CONTROLS_LINGER_MS] once
 * [controlsShouldFade] says it may, and not while the settings sheet is
 * open — the rule itself lives there, where it can be tested.
 */
@Composable
internal fun ControlsAutoHide(controlsShown: Boolean, isPlaying: Boolean, scrubbing: Boolean, settingsShown: Boolean, onHide: () -> Unit) {
    LaunchedEffect(controlsShown, isPlaying, scrubbing, settingsShown) {
        if (!controlsShown || settingsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = isPlaying, isScrubbing = scrubbing)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        onHide()
    }
}
