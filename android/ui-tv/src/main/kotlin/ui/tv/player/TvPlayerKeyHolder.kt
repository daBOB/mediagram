package ui.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag

/** Finds the screen itself in a test: the node that holds the remote while the controls are away. */
internal const val TvPlayerScreenTag = "tv-player-screen"

/**
 * What holds the remote while the controls are away, so no key is lost to
 * a focus that went with them ([root]). While they are up it cannot be
 * focused at all ([canHold] is false), so moving around them never lands
 * on the picture.
 *
 * A sibling laid over the whole screen, behind everything drawn, and
 * never an ancestor of any of it. Compose reads a Back key as a move of
 * focus out of whatever holds it (`FocusDirection.Exit`), and a focusable
 * ancestor is exactly where such a move lands: the notes column or a
 * failure's Retry would give the remote up to it, the move would count as
 * the key's answer, and the Back would never reach [TvPlayerBack]. With
 * nothing focusable above them, the move finds nowhere to go and the key
 * goes on to the back dispatcher.
 *
 * Covering the screen rather than a point in a corner, so a D-pad press on
 * Retry can never find it as the nearest thing up or to the left: a search
 * only considers what lies wholly in the direction it looks.
 */
@Composable
internal fun BoxScope.TvPlayerKeyHolder(
    root: FocusRequester,
    canHold: Boolean,
) {
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .focusRequester(root)
                .focusProperties { canFocus = canHold }
                .focusable()
                .testTag(TvPlayerScreenTag),
    )
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
