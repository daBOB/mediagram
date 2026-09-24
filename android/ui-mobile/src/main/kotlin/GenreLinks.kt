package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import designsystem.Spacing

/**
 * Each genre as a link to its shelf — shared by a film's own page and a
 * show's header, the same way `genreLinks` in the web's `film-page.js` is.
 * `null` when there are none, so a title with nothing tagged leaves the
 * space out rather than showing an empty row.
 */
@Composable
internal fun GenreLinks(names: List<String>, onOpen: (String) -> Unit) {
    if (names.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for (name in names) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(role = Role.Button) { onOpen(name) },
            )
        }
    }
}
