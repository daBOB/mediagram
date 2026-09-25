package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import catalog.SearchRow
import catalog.searchWhy
import catalog.watchedFractionOf
import designsystem.Spacing
import model.Progress

/**
 * One hit: title, where it sits, why it matched, what it is, and — if this
 * viewer has been partway through it — the same progress rule a shelf card
 * draws. A tap plays it directly, the same dialog a shelf card opens; a
 * search result is a set, not a link to somewhere else. Ported from
 * `search-view.js`'s row, one flat list rather than shelves: the point of
 * searching a hundred lessons named "Definition" is that the best answer is
 * first, and a heading would bury it.
 *
 * A document is the one hit this cannot open — the player would be handed a
 * PDF — so it is shown and not tapped, the same rule [ItemRow] already
 * follows for one inside a course.
 */
@Composable
internal fun SearchResultRow(row: SearchRow, progress: Progress?, watched: Boolean, onPlay: (String) -> Unit) {
    val set = row.set
    val playable = isPlayable(set)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (playable) it.clickable(role = Role.Button) { onPlay(set.setId) } else it }
            .padding(vertical = Spacing.small),
    ) {
        Text(
            text = "${if (watched) "✓ " else ""}${set.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (playable) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!playable) {
            Text(DOCUMENT_REASON, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        locationOf(set)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // The reason a summary hit is worth showing at all.
        row.excerpt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        searchWhy(row.matched)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        searchMetaLineOf(set).takeIf(String::isNotEmpty)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (row.held) OfflineBadge(modifier = Modifier.padding(top = Spacing.extraSmall))
        watchedFractionOf(progress)?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.extraSmall),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = Spacing.small))
    }
}
