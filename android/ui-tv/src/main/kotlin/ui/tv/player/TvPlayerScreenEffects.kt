package ui.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
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
 *
 * The up-next card, when it appears, takes the remote onto Play now — the
 * phone's card is one tap away wherever a finger already is, and on a
 * television the only way to make it as near is to put the remote on it.
 * Never out from under a viewer in the middle of something, though: not
 * from the settings panel or the list dialog ([busy]), whose own keys a
 * card must not start answering, nor from the seek bar, where the next
 * Right of someone seeking into the last half-minute has to keep moving
 * the film. The card stays one press up from the seek bar for all of them.
 * With the panel closed while the card is up, Play now is where the
 * remote goes rather than the gear: the card is what is waiting on an
 * answer.
 *
 * A failed title puts the remote on its Retry ([retry]), the one thing
 * left to press. With the controls away and the notes open, the remote
 * goes to the notes ([notesRegion]) rather than the screen, so Up and
 * Down page through them.
 */
@Composable
internal fun TvRemoteFollowsControls(
    barShown: Boolean,
    settingsOpen: Boolean,
    upNextShown: Boolean,
    landing: TvControlsLanding,
    root: FocusRequester,
    focus: TvPlayerFocus,
    failed: Boolean = false,
    notesOpen: () -> Boolean = { false },
    busy: () -> Boolean = { false },
) {
    LaunchedEffect(barShown, settingsOpen, upNextShown, failed) {
        when {
            settingsOpen -> Unit
            failed -> focus.retry.requestFocus()
            !barShown -> if (notesOpen()) focus.notesRegion.requestFocus() else root.requestFocus()
            upNextShown -> if (!busy()) focus.upNext.requestFocus()
            landing == TvControlsLanding.SeekBar -> focus.seekBar.requestFocus()
            landing == TvControlsLanding.Settings -> focus.settings.requestFocus()
            else -> focus.playPause.requestFocus()
        }
    }
}

/**
 * The table's Back row, answered from the dispatcher rather than as a key
 * so a Back that is not one — a gesture, the dispatcher itself — does the
 * same: the panel closes first, then the up-next card goes, then the
 * notes, then the controls, and only then is the player left.
 */
@Composable
internal fun TvPlayerBack(
    barShown: Boolean,
    onSeekBar: Boolean,
    settingsOpen: Boolean,
    upNextShown: Boolean,
    notesOpen: Boolean,
    onClosePanel: () -> Unit,
    onCancelUpNext: () -> Unit,
    onCloseNotes: () -> Unit,
    onHideControls: () -> Unit,
    onLeave: () -> Unit,
) {
    BackHandler {
        val action = tvKeyAction(Key.Back, barShown, onSeekBar, panelOpen = settingsOpen, upNextShown = upNextShown, notesOpen = notesOpen)
        when (action) {
            TvKeyAction.ClosePanel -> onClosePanel()
            TvKeyAction.CancelUpNext -> onCancelUpNext()
            TvKeyAction.CloseNotes -> onCloseNotes()
            TvKeyAction.HideControls -> onHideControls()
            else -> onLeave()
        }
    }
}

/**
 * Closes what would otherwise outlive what it belongs to. Marks go with the
 * title they belong to; a list choice still open when they go would come
 * back over whatever opens next — and so does a change of title ([setId])
 * by Next, Previous or up next, which leaves the marks in place for the
 * title that follows: a dialog left open would file that title under a
 * choice the viewer made for the one before. Only a real change: the same
 * title recomposed after a configuration change keeps its dialog. The
 * settings panel is drawn over a player; without one (a restore that comes
 * back before the player is built) it would be open but nowhere, and still
 * taking the D-pad and Back for itself.
 */
@Composable
internal fun TvPlayerOverlaysReset(
    setId: String,
    marksGone: Boolean,
    playerGone: Boolean,
    closeList: () -> Unit,
    closePanel: () -> Unit,
) {
    var listFor by rememberSaveable { mutableStateOf(setId) }
    LaunchedEffect(setId) {
        if (setId != listFor) {
            listFor = setId
            closeList()
        }
    }
    LaunchedEffect(marksGone) { if (marksGone) closeList() }
    LaunchedEffect(playerGone) { if (playerGone) closePanel() }
}
