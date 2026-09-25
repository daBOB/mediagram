package ui.catalog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Starts a run at its first title — `listControls`' own "Play all" in
 * `collections-view.js`, and the Kids wall's equivalent for "Marked by
 * hand". Shown only where the caller already knows a run is non-empty:
 * there is nothing to play all of otherwise.
 */
@Composable
internal fun PlayAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text("▶ Play all")
    }
}
