package ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Asked before signing out, and worded against start over's: this ends the
 * login and takes the account's catalog with it, but keeps what is the
 * device's own, so signing back in is all that is left to do.
 */
@Composable
internal fun SignOutConfirmation(
    asking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!asking) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = {
            Text(
                "This signs this device out of Telegram and removes the library it " +
                    "was reading. The api_id, api_hash and TMDB key stay; signing in " +
                    "again is all it takes to come back.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text("Sign out") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
