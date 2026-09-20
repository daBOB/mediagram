package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import designsystem.Spacing

/**
 * Where the poster artwork comes from, and whether it is switched on.
 *
 * Fetching posters is optional — the catalog reads perfectly well as
 * initials — so this screen says that rather than implying a key is
 * required. It says whether one is currently stored and never what it is:
 * a stored secret is confirmed, not displayed, the same rule
 * [TelegramApplicationScreen] applies to `api_hash`.
 *
 * Saving a blank field is how a key is cleared without a trip through
 * Start over — [settings.keyOrNull] treats a blank the same on the way
 * back in, so nothing here has to duplicate that rule.
 */
@Composable
fun TmdbKeyScreen(hasKey: Boolean, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.large)) {
        Text(text = "Fetch poster artwork", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Optional. Create a free API key at themoviedb.org and paste its v3 key " +
                "here to let the catalog show artwork instead of initials.",
            modifier = Modifier.padding(vertical = Spacing.small),
        )
        Text(text = if (hasKey) "A key is currently stored." else "No key is stored.")
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("TMDB v3 API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(vertical = Spacing.small),
        )
        Button(onClick = { onSave(key); key = "" }) { Text("Save") }
    }
}
