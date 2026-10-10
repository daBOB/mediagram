package ui.catalog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable

/** What a fetch reported, or what stopped it — shown over whichever library screen is up when it finishes. */
@Composable
internal fun FetchResultDialog(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update library") },
        text = { Text(message) },
        confirmButton = { CatalogueTextButton(onClick = onDismiss) { Text("OK") } },
    )
}
