package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.SetCard
import designsystem.Overscan
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet
import ui.tv.TvTextRow
import ui.tv.setup.TvConfirmDialog

/**
 * One list's titles, in the order they were filed — `listView` in
 * collections-view.js and the phone's `ListScreen`: Rename and Delete list
 * above, and each title with a way to take it back off the list.
 *
 * A wall of plates where the phone has rows, because a title is a plate
 * everywhere else on this surface; each plate carries its own "Remove"
 * beneath it, so taking a title off is one press down from the title it
 * takes off, never a mode to enter first. The remote lands on the first
 * plate — or on the one [restoreKey] names, coming back from a title — as
 * on every other wall; Rename and Delete are one press up. An empty list
 * lands on Rename instead, the one thing left to do with it.
 *
 * "Play all" starts the list at its first title and plays on through it —
 * the phone's own `PlayAllButton`, first in the row above, and only while
 * the list has a title to start on. Coming back from the run it started,
 * [restoreKey] is [TvPlayAllKey] and the remote lands on it again rather
 * than on the first plate, which the viewer never pressed.
 *
 * Rename asks with [TvListNameQuestion]; Delete asks first with the phone's
 * own words, since the list is gone after it — the titles never are.
 */
@Composable
fun TvList(
    list: ListOfSets,
    sets: List<MediaSet>,
    onPlay: (setId: String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemove: (setId: String) -> Unit,
    restoreKey: String? = null,
    onPlayAll: () -> Unit = {},
    heldIds: Set<String> = emptySet(),
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    if (renaming) {
        TvListNameQuestion(
            initial = list.name,
            onConfirm = { name ->
                renaming = false
                onRename(name)
            },
            onDismiss = { renaming = false },
        )
        return
    }

    // An empty list has no plate to land on, and a list emptied by its last
    // Remove has just lost the one the remote was on.
    val renameFocus = remember { FocusRequester() }
    val playAllFocus = remember { FocusRequester() }
    val backFromPlayAll = restoreKey == TvPlayAllKey && sets.isNotEmpty()
    LaunchedEffect(sets.isEmpty()) { if (sets.isEmpty()) renameFocus.requestFocus() }
    // Taking a title off takes away the plate the remote is on; the title
    // beside it — after it, or before it at the end — is where it goes next.
    var afterRemoval by remember { mutableStateOf<String?>(null) }
    val remove = { setId: String ->
        afterRemoval = neighbourOf(sets, MediaSet::setId, setId)
        onRemove(setId)
    }

    val header: @Composable () -> Unit = {
        Column {
            TvCountedHeading(list.name, sets.size)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.large)) {
                if (sets.isNotEmpty()) {
                    TvTextRow(text = "▶ Play all", onClick = onPlayAll, focusRequester = playAllFocus)
                    // Asked for from inside the header, which the wall only
                    // composes once it lays it out: a request made before
                    // that would have nothing to land on.
                    if (backFromPlayAll) LaunchedEffect(Unit) { playAllFocus.requestFocus() }
                }
                TvTextRow(text = "Rename", onClick = { renaming = true }, focusRequester = renameFocus)
                TvTextRow(text = "Delete list", onClick = { deleting = true })
            }
        }
    }
    TvPage(takesArrivalFocus = !backFromPlayAll) {
        if (sets.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
                header()
                TvQuietLine("Nothing on this list yet. Add titles from the player.", Modifier.padding(top = Spacing.large))
            }
        } else {
            TvWall(
                items = sets,
                key = MediaSet::setId,
                restoreKey = afterRemoval ?: restoreKey,
                onOpen = { set -> onPlay(set.setId) },
                header = header,
                plate = { set, modifier, onOpen ->
                    TvPlateWithAction(label = "Remove", onAction = { remove(set.setId) }) {
                        TvSetPlate(card = SetCard(set, caption = "", progress = null, watched = false, held = set.setId in heldIds), onOpen = onOpen, modifier = modifier)
                    }
                },
            )
        }
    }

    if (deleting) {
        TvConfirmDialog(
            title = "Delete \"${list.name}\"?",
            body = "The titles stay in the library.",
            confirmLabel = "Delete list",
            confirm = {
                deleting = false
                onDelete()
            },
            cancel = { deleting = false },
        )
    }
}

/** What a list remembers it opened when "Play all" started it: no title's id, so never mistaken for a plate. */
internal const val TvPlayAllKey = "list:play-all"
