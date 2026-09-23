package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import model.ListOfSets

/**
 * Filing the open title into one or more lists, from the player.
 *
 * The web asks with a numbered `window.prompt()` (`addToButton` in
 * player.js) because its Collections shelf — where a list is actually made
 * — is one click away on the same page. A phone viewer mid-film has no such
 * shelf on screen, so this offers a checkbox per list, ticked or not, and an
 * inline way to start a new one without leaving the player. A deliberate
 * difference from the web's own dialog, not a parity gap — the phase's
 * Related Code Files call for exactly this shape.
 */
@Composable
internal fun AddToListDialog(
    lists: List<ListOfSets>,
    memberOf: Set<String>,
    onToggle: (id: String, included: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to list") },
        text = {
            Column {
                if (lists.isEmpty()) {
                    Text("No lists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(items = lists, key = ListOfSets::id) { list ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = list.id in memberOf,
                                    onCheckedChange = { checked -> onToggle(list.id, checked) },
                                )
                                Text(list.name)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("New list") },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(newName.trim()); newName = "" },
                enabled = newName.isNotBlank(),
            ) { Text("New list") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
