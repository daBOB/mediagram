package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.RowContent
import catalog.Shelf
import catalog.homeRowsOf
import catalog.keyOf
import designsystem.Spacing
import model.WatchSnapshot

private const val LATEST_LIMIT = 48
private val LATEST_TITLES = setOf("Latest films", "Latest series", "Latest courses")

/**
 * Films, shows and courses, newest arrival first — a Compose port of
 * `utility-pages.js#renderLatest`. Built from the same [homeRowsOf] the
 * start page's own Latest rows already are, kept to the three that name
 * one, rather than a second "newest by kind" computation of its own.
 */
@Composable
internal fun LatestScreen(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val rows = remember(shelves, watch, heldIds) {
        homeRowsOf(shelves, watch, heldIds, limit = LATEST_LIMIT).filter { it.title in LATEST_TITLES }
    }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(text = "Latest", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Newest arrivals first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        for (row in rows) {
            val content = row.content as? RowContent.Entries ?: continue
            if (content.entries.isEmpty()) continue
            item(key = "heading-${row.title}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
                )
            }
            items(items = content.entries, key = { "${row.title}/${keyOf(it)}" }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection, heldIds)
            }
        }
    }
}
