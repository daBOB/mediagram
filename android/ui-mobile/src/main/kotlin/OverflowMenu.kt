package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The five things the overflow menu can do, in the order they are shown.
 *
 * Refreshing the library and fetching its details were two items and are
 * one. They were always done in that order and only in that order: a
 * refresh brings sets the channel has gained, and those are exactly the
 * sets with no synopsis and no artwork yet, so fetching without refreshing
 * first fills in gaps while leaving new ones unlisted. Two items made a
 * viewer remember a sequence the app already knew.
 *
 * It is named "Update library" and not "Refresh library", though refreshing
 * is the half anyone would name. Refreshing is a few seconds of one round
 * trip; fetching is minutes of HTTP over hundreds of titles, and a word
 * promising the first while doing the second is a lie a viewer only catches
 * by waiting.
 *
 * The TMDB key sits directly under the update it configures, and is named
 * with an ellipsis because it opens a screen to fill in rather than doing
 * anything itself.
 *
 * Settings sits directly after System, ahead of the update it does not
 * configure: both are about the app and the account rather than the
 * library's contents, and grouping them keeps the update sequence —
 * update, key, start over — together and in the order described below.
 *
 * Updating is third and start over last, two items apart: updating is the
 * most-used of the five and starting over discards this device's Telegram
 * session, and the most frequent should not sit beside the most
 * destructive. The confirmation dialog is a backstop, not a reason to
 * invite the mis-tap.
 *
 * [updateDisabledReason] is `null` when the action is available and a
 * sentence when it is not — a run already in flight. [updateNote] is said
 * under the label while the item stays tappable: with no TMDB key the
 * refresh still works and only the artwork half is skipped, which is worth
 * doing and worth saying. An item that silently does less than its name is
 * worse than one that says what it will leave out.
 */
data class MenuActions(
    val onSystem: () -> Unit,
    val onSettings: () -> Unit,
    /**
     * Re-read the channel's newest index, then fill in what it has no room
     * for: the descriptions nobody wrote and the artwork no index carries.
     * In that order, because the second is about what the first brought in.
     */
    val onUpdate: () -> Unit,
    val onTmdbKey: () -> Unit,
    val onStartOver: () -> Unit,
    val updateDisabledReason: String? = null,
    val updateNote: String? = null,
)

/**
 * The overflow menu itself — the icon that opens it and the five items
 * behind it, the same wherever [LibraryScaffold] renders it. [onAskStartOver]
 * is separate from the rest of [menu] because the item it is bound to does
 * not act immediately: the caller owns the confirmation that follows, and
 * this only asks for it.
 */
@Composable
internal fun OverflowMenu(menu: MenuActions, onAskStartOver: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.semantics { contentDescription = "Menu" },
    ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("System") },
            onClick = { menuExpanded = false; menu.onSystem() },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = { menuExpanded = false; menu.onSettings() },
        )
        MenuItem(
            label = "Update library",
            note = menu.updateDisabledReason ?: menu.updateNote,
            enabled = menu.updateDisabledReason == null,
            onClick = { menuExpanded = false; menu.onUpdate() },
        )
        DropdownMenuItem(
            text = { Text("TMDB key…") },
            onClick = { menuExpanded = false; menu.onTmdbKey() },
        )
        DropdownMenuItem(
            text = { Text("Start over") },
            onClick = { menuExpanded = false; onAskStartOver() },
        )
    }
}

/**
 * An item that says something under its own label.
 *
 * Two cases, and the note reads the same in both: unavailable, where a
 * greyed row with nothing under it leaves a viewer tapping at it to find
 * out what is wrong; and available but about to do less than its name says,
 * where the note is the part it will skip.
 */
@Composable
private fun MenuItem(label: String, note: String?, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}
