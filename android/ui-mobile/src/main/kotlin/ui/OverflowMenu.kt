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
 * The overflow menu itself — the icon that opens it and the five items
 * behind it, the same wherever [LibraryScaffold] renders it. [onAskStartOver]
 * is separate from the rest of [menu] because the item it is bound to does
 * not act immediately: the caller owns the confirmation that follows, and
 * this only asks for it.
 */
@Composable
internal fun OverflowMenu(
    menu: MenuActions,
    onAskStartOver: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.semantics { contentDescription = "Menu" },
    ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("System") },
            onClick = {
                menuExpanded = false
                menu.onSystem()
            },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = {
                menuExpanded = false
                menu.onSettings()
            },
        )
        MenuItem(
            label = "Update library",
            note = menu.updateDisabledReason ?: menu.updateNote,
            enabled = menu.updateDisabledReason == null,
            onClick = {
                menuExpanded = false
                menu.onUpdate()
            },
        )
        DropdownMenuItem(
            text = { Text("TMDB key…") },
            onClick = {
                menuExpanded = false
                menu.onTmdbKey()
            },
        )
        DropdownMenuItem(
            text = { Text("Start over") },
            onClick = {
                menuExpanded = false
                onAskStartOver()
            },
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
private fun MenuItem(
    label: String,
    note: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
