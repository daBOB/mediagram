package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import player.CONTROLS_LINGER_MS
import player.PlayerViewModel
import player.controlsShouldFade
import ui.common.player.KeepScreenOnWhile
import ui.common.player.PlayerLifecycle

/**
 * The side effects [PlayerScreen] runs for its own lifecycle rather than for
 * anything on screen. The shared [PlayerLifecycle] (stop when left for real, save on
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
 * Takes the card and the top bar away after [CONTROLS_LINGER_MS] once
 * [controlsShouldFade] says they may — never while a menu or the episode
 * sidebar is open; the rule itself lives there, where it can be tested.
 */
@Composable
internal fun ControlsAutoHide(controlsShown: Boolean, isPlaying: Boolean, scrubbing: Boolean, menuOrSidebarOpen: Boolean, onHide: () -> Unit) {
    LaunchedEffect(controlsShown, isPlaying, scrubbing, menuOrSidebarOpen) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = isPlaying, isScrubbing = scrubbing, menuOrSidebarOpen = menuOrSidebarOpen)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        onHide()
    }
}
