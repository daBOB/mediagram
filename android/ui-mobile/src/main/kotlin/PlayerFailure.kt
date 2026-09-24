package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import designsystem.Spacing

/**
 * A failed set's message, and Retry — the phone's touch equivalent of the
 * web's seek-to-retry (there is no button there; a viewer drags the scrub
 * bar). A phone's failures are almost always the network rather than a
 * broken file, so re-opening the same set at wherever it was last saved to
 * is usually enough; recorded as a deliberate, phone-only addition rather
 * than web debt.
 */
@Composable
internal fun PlayerFailure(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.large), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onRetry) {
                Text(text = "Retry", color = Color.White)
            }
        }
    }
}
