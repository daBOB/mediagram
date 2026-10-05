package ui.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * still be there when it closes. A menu and the episode list
 * ([menuOrSidebarOpen]) hold them as the phone's sheet does, for the same
 * return: Back from either lands on the control that opened it.
 */
@Composable
internal fun TvControlsAutoHide(
    controlsShown: Boolean,
    state: PlayerUiState,
    presses: Int,
    held: Boolean,
    menuOrSidebarOpen: Boolean,
    onHide: () -> Unit,
) {
    LaunchedEffect(controlsShown, state, presses, held, menuOrSidebarOpen) {
        if (!controlsShown) return@LaunchedEffect
        val fades = controlsShouldFade(isPlaying = state is PlayerUiState.Playing, isScrubbing = held, menuOrSidebarOpen = menuOrSidebarOpen)
        if (!fades) return@LaunchedEffect
        delay(CONTROLS_LINGER_MS)
        onHide()
    }
}

/**
 * Wherever the controls go, the remote goes with them: onto the control the
 * key that raised them asked for ([landing]), or back to the screen itself
 * ([root]) when they leave. While a menu or the episode list is
 * open ([panelOpen]) it takes the remote for itself; when it closes,
 * [landing] says where the remote goes back to — the control that opened it
 * ([TvPlayerFocus.opener]).
 *
 * The up-next card, when it appears, takes the remote onto Play now — the
 * phone's card is one tap away wherever a finger already is, and on a
 * television the only way to make it as near is to put the remote on it.
 * Never out from under a viewer in the middle of something, though: not
 * from a menu or the list dialog ([busy]), whose own keys a
 * card must not start answering, nor from the seek bar, where the next
 * Right of someone seeking into the last half-minute has to keep moving
 * the film. The card stays one press up from the seek bar for all of them.
 * With a menu closed while the card is up, Play now is where the
 * remote goes rather than the tool: the card is what is waiting on an
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
    panelOpen: Boolean,
    upNextShown: Boolean,
    landing: TvControlsLanding,
    root: FocusRequester,
    focus: TvPlayerFocus,
    failed: Boolean = false,
    notesOpen: () -> Boolean = { false },
    busy: () -> Boolean = { false },
) {
    LaunchedEffect(barShown, panelOpen, upNextShown, failed) {
        when {
            panelOpen -> Unit
            failed -> focus.retry.requestFocus()
            !barShown -> if (notesOpen()) focus.notesRegion.requestFocus() else root.requestFocus()
            upNextShown -> if (!busy()) focus.upNext.requestFocus()
            landing == TvControlsLanding.SeekBar -> focus.seekBar.requestFocus()
            landing == TvControlsLanding.Opener -> focus.opener.requestFocus()
            else -> focus.playPause.requestFocus()
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
 * menus and the episode list are drawn over a player; without one (a restore that comes
 * back before the player is built) one would be open but nowhere, and still
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
