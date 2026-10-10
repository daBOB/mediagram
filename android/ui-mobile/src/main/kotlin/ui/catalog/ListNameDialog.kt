package ui.catalog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.chrome.NoDialogAnimation

/**
 * `window.prompt` for a list's name, as a Material dialog with a text field
 * — used for both "New list" and rename, the same two prompts
 * `collections-view.js` opens with `window.prompt("Name for the list", ...)`.
 */
@Composable
internal fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            NoDialogAnimation()
            Text(title)
        },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            CatalogueTextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { CatalogueTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
