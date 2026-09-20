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
 * The last setup step: package URL and key, pasted in during provisioning
 * (crude on purpose; QR is a later round). The key is a secret and is
 * always masked; it is never logged. [error] says why the last attempt was
 * not stored, and never quotes what was typed.
 */
@Composable
fun SettingsScreen(error: String?, onSave: (url: String, keyB64: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(text = "Paste your package URL and key")
        if (error != null) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Package URL") })
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Package key") },
            visualTransformation = PasswordVisualTransformation(),
            // Masking is only for whoever can see the screen. Declaring the
            // field a password is what keeps the IME from predicting on it
            // and learning it into a dictionary that, on a cloud-syncing
            // keyboard, leaves the device.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(onClick = { onSave(url, key) }, enabled = url.isNotBlank() && key.isNotBlank()) {
            Text("Save")
        }
    }
}
