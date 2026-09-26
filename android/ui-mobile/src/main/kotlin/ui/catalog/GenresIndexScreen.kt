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
import androidx.compose.ui.Modifier
import catalog.GenreIndexEntry
import designsystem.Spacing

/**
 * Every genre the library holds, as its own page of tiles — a Compose port
 * of `utility-pages.js#renderGenres`.
 */
@Composable
internal fun GenresIndexScreen(
    genres: List<GenreIndexEntry>,
    columns: Int,
    onOpenGenre: (String) -> Unit,
) {
    if (genres.isEmpty()) {
        CenteredMessage("Nothing in the library has a genre recorded.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(text = "Genres", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = countOf(genres.size, "genre"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        items(items = genres, key = GenreIndexEntry::name) { entry ->
            PosterCard(
                posterPath = entry.art,
                title = entry.name,
                caption = countOf(entry.count, "title"),
                modifier = Modifier,
                onClick = { onOpenGenre(entry.name) },
            )
        }
    }
}
