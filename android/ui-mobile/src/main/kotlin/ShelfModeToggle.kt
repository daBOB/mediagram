package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import settings.ShelfView

/**
 * "List · Grid" — `shelfToggle` in the web's `app.js`. The one in use is
 * marked selected rather than disabled, as the web marks it pressed, so a
 * screen reader says which one this is and the control stays reachable.
 */
@Composable
internal fun ShelfModeToggle(current: ShelfView, onChoose: (ShelfView) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.semantics { contentDescription = "How to show this shelf" }) {
        for ((view, label) in listOf(ShelfView.LIST to "List", ShelfView.GRID to "Grid")) {
            val on = view == current
            TextButton(onClick = { if (!on) onChoose(view) }, modifier = Modifier.semantics { selected = on }) {
                Text(
                    text = label,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
