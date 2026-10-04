package ui.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import catalog.profile.REMOVE
import catalog.profile.removeQuestion
import model.Profile

/**
 * Asked before Manage removes anyone, in the web's words — its
 * `window.confirm`. A grown-up's kids go with it, and the question says so.
 */
@Composable
internal fun RemoveProfileDialog(
    profile: Profile,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(removeQuestion(profile)) },
        confirmButton = {
            TextButton(onClick = {
                onRemove()
                onDismiss()
            }) { Text(REMOVE) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
