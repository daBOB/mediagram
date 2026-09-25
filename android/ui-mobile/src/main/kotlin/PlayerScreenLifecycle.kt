package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import player.PlayerViewModel
import player.open

/**
 * The side effects [PlayerScreen] runs for its own lifecycle rather than for
 * anything on screen — split out to keep that file under the project's line
 * guideline. Stops playback when the screen leaves composition for real, not
 * on a rotation (see [shouldStopOnDispose]), saves on `ON_STOP` as a backstop
 * for a kill that skips `onDispose` entirely, keeps the screen awake while
 * [isPlaying], and hides the system bars for as long as this screen holds
 * them — see [ImmersiveEffect].
 */
@Composable
internal fun PlayerLifecycleEffects(viewModel: PlayerViewModel, isPlaying: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        onDispose {
            val stillInPip = activity?.isInPictureInPictureMode == true
            if (shouldStopOnDispose(activity?.isChangingConfigurations == true, stillInPip)) {
                viewModel.stop()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.save()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    KeepScreenOnWhile(isPlaying = isPlaying)
    ImmersiveEffect()
}

/**
 * A rotation or a picture-in-picture resize disposes and recreates
 * `PlayerScreen`'s whole composition exactly the way leaving it for the
 * catalog does only on an OEM that ignores the manifest's own
 * `android:configChanges` for one of the two; genuinely leaving the
 * screen is told apart from either by whether the Activity is mid
 * configuration change or still in picture-in-picture at the moment this
 * runs. Stopping for either would restart the same set from zero the next
 * time the device turns or the window is entered, which is worse than the
 * drop-to-catalog bug this replaced.
 */
internal fun shouldStopOnDispose(isChangingConfigurations: Boolean, isInPictureInPicture: Boolean): Boolean =
    !isChangingConfigurations && !isInPictureInPicture

/**
 * Opens [setId], keeps its run current, and carries out an up-next switch
 * once the VM asks for one — split out of [PlayerScreen] to keep that file
 * under the project's line guideline.
 *
 * The switch moves `LibraryPositions` through [onSwitch] before anything
 * else: a rotation or process restore reads that saved frame, and one still
 * naming the title that had just finished would replay it instead of the
 * one actually playing. `viewModel.open` for the new id then runs as usual,
 * from the `LaunchedEffect(setId)` below, once `onSwitch` recomposes this
 * with it.
 */
@Composable
internal fun PlayerNavigationEffects(
    viewModel: PlayerViewModel,
    setId: String,
    run: List<String>,
    fsk: String?,
    onSwitch: (setId: String, run: List<String>) -> Unit,
) {
    val pendingSwitch by viewModel.pendingSwitch.collectAsStateWithLifecycle()
    LaunchedEffect(setId) { viewModel.open(setId, run, fsk) }
    // The run alone changing (the catalog finishing its own load after this
    // title already opened) must not reopen the title — `open` would, since
    // that is not `sameTitle` to it either; only the run itself moves.
    LaunchedEffect(setId, run) { viewModel.updateRun(setId, run) }
    LaunchedEffect(pendingSwitch) {
        pendingSwitch?.let { switch ->
            onSwitch(switch.setId, switch.run)
            viewModel.switchAcknowledged()
        }
    }
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
