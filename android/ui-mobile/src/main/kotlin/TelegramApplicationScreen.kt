package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import designsystem.Spacing

/**
 * Step one: the Telegram *application* identity.
 *
 * The screen says where the two values come from, because nobody handed a
 * tablet can guess that they are created at my.telegram.org, and this is
 * the only screen in the app whose answer is not already in the person's
 * head.
 *
 * `api_id` is a number and is shown as typed. `api_hash` is a secret: it is
 * masked, and declared a password field so the keyboard stops predicting on
 * it and stops learning it into a dictionary that, on a cloud-syncing
 * keyboard, would leave the device. Neither is kept across a
 * configuration change — a secret does not belong in a saved-state bundle
 * the system may write to disk.
 */
@Composable
fun TelegramApplicationScreen(error: String?, onSubmit: (apiId: String, apiHash: String) -> Unit) {
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(text = "Connect this device to Telegram", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Sign in at my.telegram.org, open “API development tools”, and copy the " +
                "api_id and api_hash it shows for your application.",
            modifier = Modifier.padding(vertical = Spacing.small),
        )
        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = apiId,
            onValueChange = { apiId = it },
            label = { Text("api_id") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = apiHash,
            onValueChange = { apiHash = it },
            label = { Text("api_hash") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            onClick = { onSubmit(apiId, apiHash) },
            enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
        ) {
            Text("Continue")
        }
    }
}
