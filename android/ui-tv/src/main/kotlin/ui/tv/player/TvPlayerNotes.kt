package ui.tv.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import player.PlayerNotes

/**
 * Where the notes column stands with the remote. [focused] follows the
 * column, so a Back that closes it from inside knows to hand the remote
 * on ([returnToButton]) rather than drop it with the column; [asked] is
 * set by the Notes button, the one way of opening the column that means
 * "and take me there". Plain fields, not state: each is read once, by
 * [TvNotesFollow], after the change that set it.
 */
internal class TvNotesFocus {
    var focused = false
    var asked = false
    var returnToButton = false

    /** The Notes button: opening from it takes the remote into the column. */
    fun toggleFromButton(
        open: Boolean,
        toggle: () -> Unit,
    ) {
        asked = !open
        toggle()
    }

    /** Back, which closes the column: from inside it, the remote goes on to the Notes button. */
    fun closeFromBack(toggle: () -> Unit) {
        returnToButton = focused
        toggle()
    }
}

/**
 * The picture with the open title's [notes] beside it — the web's rule,
 * "a column beside the picture, not over it", since notes are screens of
 * text and text over a film hides both. A television is always a window
 * wide and tall enough for that, so none of the phone's other placements
 * (below an upright window, over a phone on its side) ever apply here.
 *
 * Left out of the column goes to the Notes [button] that opened it, while
 * the controls are up to hold one, rather than to whichever control lies
 * nearest — the seek bar, most often, where a second Left would skip the
 * film.
 *
 * The picture keeps its place in the tree whether the column is there or
 * not, so opening the notes narrows the film rather than rebuilding it.
 */
@Composable
internal fun TvNotesBeside(
    notes: PlayerNotes?,
    region: FocusRequester,
    focus: TvNotesFocus,
    button: FocusRequester?,
    stage: @Composable BoxScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = stage)
        if (notes != null && notes.open) {
            TvNotesPanel(
                blocks = notes.blocks,
                region = region,
                onFocusChanged = { focus.focused = it },
                left = button,
                modifier = Modifier.fillMaxWidth(NOTES_SHARE).fillMaxHeight(),
            )
        }
    }
}

/** About the share of the width the web gives its column. */
private const val NOTES_SHARE = 0.38f

/**
 * Moves the remote as the column opens and closes. Opened by the Notes
 * button, or while the controls are away, the column takes the remote —
 * the one place left to put it that is not the film. A lesson's notes,
 * which open by themselves with the controls up, wait for them to go
 * ([TvRemoteFollowsControls]) instead of pulling the remote off the
 * transport under a viewer about to press play.
 *
 * Closed, the column hands the remote back: to the screen itself with the
 * controls away, or to the Notes button when Back closed it from inside
 * with the controls up. [busy] — the settings panel — keeps the remote
 * where it is either way. A [failed] title keeps it on Retry, and takes it
 * back there when the column closes: Retry is then the only thing left to
 * press, and a remote left in a column that has gone is on nothing.
 */
@Composable
internal fun TvNotesFollow(
    notesOpen: Boolean,
    barShown: Boolean,
    focus: TvNotesFocus,
    root: FocusRequester,
    controls: TvPlayerFocus,
    busy: () -> Boolean,
    failed: () -> Boolean = { false },
) {
    LaunchedEffect(notesOpen) {
        val asked = focus.asked
        val toButton = focus.returnToButton
        focus.asked = false
        focus.returnToButton = false
        if (busy()) return@LaunchedEffect
        when {
            failed() -> if (!notesOpen) controls.retry.requestFocus()
            notesOpen -> if (asked || !barShown) controls.notesRegion.requestFocus()
            !barShown -> root.requestFocus()
            toButton -> controls.notes.requestFocus()
        }
    }
}
