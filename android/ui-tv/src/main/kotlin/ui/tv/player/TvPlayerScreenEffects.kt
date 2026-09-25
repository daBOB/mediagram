package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import player.controlsShouldFade

/**
 * Takes the controls away after a while of playing, by the shared rule and
 * on the shared clock; every one of [presses] starts the wait again.
 *
 * [held] keeps them up whatever the film is doing. The list dialog holds
 * them the way a drag holds the phone's: its keys go to its own window, so
 * no press here restarts the fade, and the controls it returns to must
 * still be there when it closes. The settings panel holds them as the
 * phone's sheet does, for the same return: Back from it lands on the gear
 * that opened it.
 */
@Composable
internal fun TvControlsAutoHide(
    controlsShown: Boolean,
    state: PlayerUiState,
    presses: Int,
    held: Boolean,
    onHide: () -> Unit,
) {
    LaunchedEffect(controlsShown, state, presses, held) {
        if (!controlsShown) return@LaunchedEffect
        if (!controlsShouldFade(isPlaying = state is PlayerUiState.Playing, isScrubbing = held)) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        onHide()
    }
}

/**
 * Wherever the controls go, the remote goes with them: onto the control the
 * key that raised them asked for ([landing]), or back to the screen itself
 * ([root]) when they leave. While the settings panel is open it takes the
 * remote for itself; when it closes, [landing] says the gear.
 */
@Composable
internal fun TvRemoteFollowsControls(
    barShown: Boolean,
    settingsOpen: Boolean,
    landing: TvControlsLanding,
    root: FocusRequester,
    focus: TvPlayerFocus,
) {
    LaunchedEffect(barShown, settingsOpen) {
        when {
            settingsOpen -> Unit
            !barShown -> root.requestFocus()
            landing == TvControlsLanding.SeekBar -> focus.seekBar.requestFocus()
            landing == TvControlsLanding.Settings -> focus.settings.requestFocus()
            else -> focus.playPause.requestFocus()
        }
    }
}
