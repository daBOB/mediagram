package ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ui.chrome.CatalogueTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import data.settings.ShelfView
import designsystem.Motion

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
            CatalogueTextButton(onClick = { if (!on) onChoose(view) }, modifier = Modifier.semantics { selected = on }) {
                ModeLabel(label, on)
            }
        }
    }
}

/** `.mode { transition: color var(--hover) }` (`catalog.css:15,17`). */
@Composable
private fun ModeLabel(label: String, on: Boolean) {
    val ink by animateColorAsState(
        if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.hover(),
        label = "shelf mode",
    )
    Text(text = label, color = ink, style = MaterialTheme.typography.labelLarge)
}
