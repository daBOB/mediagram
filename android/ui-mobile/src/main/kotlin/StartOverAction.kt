package ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Starting over signs this device out of Telegram and forgets everything
 * that was typed in to set it up. None of that can be undone from inside
 * the app, so it asks first — and the warning names what goes, rather than
 * saying only that something will.
 */
@Composable
fun StartOverAction(onConfirm: () -> Unit) {
    var asking by remember { mutableStateOf(false) }

    TextButton(onClick = { asking = true }) { Text("Start over") }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Start over?") },
            text = {
                Text(
                    "This signs this device out of Telegram and forgets the api_id and " +
                        "api_hash, the library address and its key, and the library itself. " +
                        "All of it has to be entered again.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onConfirm()
                    },
                ) {
                    Text("Start over")
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) { Text("Cancel") }
            },
        )
    }
}
