package ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import player.PlayerViewModel
import player.open

/**
 * Keeps playback through Activity recreation and stops when navigation
 * removes the screen for good. The Activity-scoped ViewModel survives
 * rotation on its own; this only decides when to tell it to stop or save.
 *
 * Stops playback when the screen leaves composition for real, not on a
 * rotation or a picture-in-picture resize (see [shouldStopOnDispose]).
 * A backstop for a kill that skips the dispose effect entirely — recents
 * swiped, the process trimmed — sits alongside it: an Activity can reach
 * `ON_STOP` (screen off, task-switched away) while the Composition it hosts
 * is still there, primed to resume, which the dispose effect alone would
 * not catch.
 */
@Composable
fun PlayerLifecycle(viewModel: PlayerViewModel) {
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
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.save()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * A rotation or a picture-in-picture resize disposes and recreates the
 * player screen's whole composition exactly the way leaving it for the
 * catalog does only on an OEM that ignores the manifest's own
 * `android:configChanges` for one of the two; genuinely leaving the
 * screen is told apart from either by whether the Activity is mid
 * configuration change or still in picture-in-picture at the moment this
 * runs. Stopping for either would restart the same set from zero the next
 * time the device turns or the window is entered, which is worse than the
 * drop-to-catalog bug this replaced.
 */
fun shouldStopOnDispose(
    isChangingConfigurations: Boolean,
    isInPictureInPicture: Boolean,
): Boolean = !isChangingConfigurations && !isInPictureInPicture

/**
 * Opens [setId], keeps its run current, and carries out an up-next switch
 * once the VM asks for one.
 *
 * The switch moves `LibraryPositions` through [onSwitch] before anything
 * else: a rotation or process restore reads that saved frame, and one still
 * naming the title that had just finished would replay it instead of the
 * one actually playing. `viewModel.open` for the new id then runs as usual,
 * from the `LaunchedEffect(setId)` below, once `onSwitch` recomposes this
 * with it.
 */
@Composable
fun PlayerNavigationEffects(
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
