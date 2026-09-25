package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import catalog.Entry
import designsystem.Spacing
import model.Progress

/**
 * A shelf as rows — the web's list mode, and the only way a shelf of
 * courses is shown. A row carries what the plate's caption would: a film's
 * year and runtime, a show's or a course's extent, with the same watched
 * tick, progress rule and "offline" badge a card has.
 */
@Composable
internal fun ShelfList(
    entries: List<Entry>,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.medium)) {
        items(items = entries, key = ::keyOf) { entry ->
            when (entry) {
                is Entry.Film -> ShelfRow(
                    title = entry.set.title,
                    caption = factsLine(entry.set.year, entry.set.durationSecs),
                    watched = entry.set.setId in watchedIds,
                    held = entry.set.setId in heldIds,
                    progress = watchedFractionOf(positions[entry.set.setId]),
                    onClick = { onOpenTitle(entry.set.setId) },
                )
                is Entry.Collection -> ShelfRow(
                    title = entry.name,
                    caption = extentOf(entry),
                    onClick = { onOpenCollection(entry.key) },
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ShelfRow(
    title: String,
    caption: String?,
    onClick: () -> Unit,
    watched: Boolean = false,
    held: Boolean = false,
    progress: Float? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.medium),
    ) {
        Text(if (watched) "✓ $title" else title, style = MaterialTheme.typography.bodyLarge)
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (held) OfflineBadge(modifier = Modifier.padding(top = Spacing.extraSmall))
        progress?.let { fraction ->
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.small))
        }
    }
}
