package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CatalogUiState
import catalog.GenreShelf
import catalog.genreShelf
import designsystem.Spacing
import model.WatchSnapshot

/**
 * Everything tagged with one genre: films first, then series — ported from
 * the web's `viewGenre`. There is no shelf of these on the home page on
 * either surface; a genre is only ever reached from a title that carries
 * it, the same restraint `film-page.js` and `series-header.js` keep.
 */
@Composable
fun GenreScreen(
    name: String,
    shelf: GenreShelf,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val total = shelf.films.size + shelf.series.size
    if (total == 0) {
        CenteredMessage("Nothing in the library is tagged with this genre.")
        return
    }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    // Labelled only when both kinds are there; a shelf of one kind already
    // says what it is by what is on it.
    val both = shelf.films.isNotEmpty() && shelf.series.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(text = name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = countOf(total, "title"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        if (shelf.films.isNotEmpty()) {
            if (both) sectionHeading("Movies")
            items(items = shelf.films, key = { it.set.setId }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection)
            }
        }
        if (shelf.series.isNotEmpty()) {
            if (both) sectionHeading("Series")
            items(items = shelf.series, key = { it.key }) { entry ->
                EntryCard(entry, positions, watchedIds, onOpenTitle, onOpenCollection)
            }
        }
    }
}

/**
 * Ranks the catalog against [name] and picks the column count — kept
 * beside the screen rather than in the branch that opens it, for the same
 * reason [SearchBranch] sits beside [SearchScreen].
 *
 * Shown as loading rather than as "nothing tagged" while [catalogState]
 * has not read the shelves yet — a restore that lands here before the
 * catalog is back is not yet an answer, and saying so would tell a viewer
 * their genre lost every title in it.
 */
@Composable
internal fun GenreBranch(
    name: String,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    if (catalogState !is CatalogUiState.Ready) {
        CenteredMessage("Loading your library…")
        return
    }
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    GenreScreen(name, genreShelf(catalogState.shelves, name), watch, columns, onOpenTitle, onOpenCollection)
}

private fun LazyGridScope.sectionHeading(label: String) {
    item(key = "heading-$label", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
        )
    }
}
