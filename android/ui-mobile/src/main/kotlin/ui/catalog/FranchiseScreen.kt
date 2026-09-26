package ui.catalog

import androidx.compose.foundation.layout.Arrangement
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
import catalog.Entry
import catalog.FranchisePage
import designsystem.Spacing
import model.WatchSnapshot

/**
 * One franchise's own page — a Compose port of
 * `collections-page.js#renderFranchise`: its introduction (TMDB's overview,
 * when the index carries one) and its films in release order.
 */
@Composable
internal fun FranchiseScreen(
    page: FranchisePage,
    watch: WatchSnapshot,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
) {
    val franchise = page.franchise
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val years = franchise.films.mapNotNull { it.year?.takeIf { year -> year > 0 } }
    val span = years.minOrNull()?.let { min -> "$min–${years.max()}" }
    val lead = franchise.films.find { it.backdropPath != null }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
            DepartmentHero(
                kicker = "The collection",
                title = franchise.name,
                line = listOfNotNull(countOf(franchise.films.size, "film"), span).joinToString(" · "),
                lead = lead,
                onOpenTitle = onOpenTitle,
            )
        }
        page.overview?.let { overview ->
            item(key = "overview", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
                )
            }
        }
        item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "In release order",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            )
        }
        items(items = franchise.films, key = { it.setId }) { set ->
            EntryCard(Entry.Film(set), positions, watchedIds, onOpenTitle, {})
        }
    }
}
