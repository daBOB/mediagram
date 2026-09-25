package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import designsystem.Spacing
import model.Profile

/**
 * The picker's "Remove a profile…" — the web's "Rename or remove…", which
 * only removes. The web asks for the name typed exactly; here the names are
 * listed to tap, since typing an exact name on a phone keyboard is a chore,
 * and the confirmation that follows is the web's own words.
 */
@Composable
internal fun RemoveProfileDialog(profiles: List<Profile>, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<Profile?>(null) }
    val target = picked
    if (target == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove which profile?") },
            text = {
                Column {
                    Text(
                        "Everything of theirs goes with it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    profiles.forEach { profile ->
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { picked = profile }
                                .padding(vertical = Spacing.medium),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Remove \"${target.name}\" and everything they have watched?") },
            confirmButton = { TextButton(onClick = { onRemove(target.id); onDismiss() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
