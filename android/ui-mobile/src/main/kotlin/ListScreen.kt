package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import model.ListOfSets
import model.MediaSet

/**
 * One list's titles, in the order they were filed — `listView` in
 * collections-view.js. Titles are added from the player's "Add to list"
 * dialog: `collection-add.js`'s in-list search picker is out of scope for
 * this phase, per the Requirements — only rename, delete and taking a title
 * back off the list are asked for here.
 */
@Composable
internal fun ListScreen(
    list: ListOfSets,
    sets: List<MediaSet>,
    onPlay: (setId: String) -> Unit,
    /** Starts the list at its first title — `null` with nothing to play. */
    onPlayAll: (() -> Unit)?,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemove: (setId: String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.medium),
            horizontalArrangement = if (onPlayAll != null) Arrangement.SpaceBetween else Arrangement.End,
        ) {
            onPlayAll?.let { PlayAllButton(onClick = it) }
            Row {
                TextButton(onClick = { renaming = true }) { Text("Rename") }
                TextButton(onClick = { deleting = true }) { Text("Delete list") }
            }
        }
        if (sets.isEmpty()) {
            CenteredMessage("Nothing on this list yet. Add titles from the player.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.medium),
            ) {
                items(items = sets, key = MediaSet::setId) { set ->
                    ListedRow(set = set, onPlay = { onPlay(set.setId) }, onRemove = { onRemove(set.setId) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (renaming) {
        ListNameDialog(
            title = "Name for the list",
            confirmLabel = "Rename",
            initial = list.name,
            onConfirm = { name -> renaming = false; onRename(name) },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        DeleteListConfirmation(
            name = list.name,
            onConfirm = { deleting = false; onDelete() },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun ListedRow(set: MediaSet, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(vertical = Spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(set.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

/** Asked, the same as the web's own `window.confirm` before `deleteCollection` — the titles themselves are never at risk, only the list naming them. */
@Composable
private fun DeleteListConfirmation(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$name\"?") },
        text = { Text("The titles stay in the library.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete list") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
