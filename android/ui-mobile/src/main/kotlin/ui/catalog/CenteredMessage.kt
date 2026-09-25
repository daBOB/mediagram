package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import designsystem.Spacing

/**
 * What a screen says when it has nothing to show.
 *
 * Set in the catalogue's own reading face rather than left at the default,
 * because a first run, an empty library and a failed load are the three
 * moments a viewer reads a whole sentence here, and they are exactly the
 * moments the app would otherwise stop sounding like itself.
 */
@Composable
internal fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
