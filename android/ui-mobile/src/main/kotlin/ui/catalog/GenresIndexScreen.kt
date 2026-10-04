package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.GenreIndexEntry
import catalog.spelledCountOf
import designsystem.Spacing

/** `.genre-tiles{grid-template-columns:repeat(auto-fill,minmax(13rem,1fr))}`. */
private val GENRE_TILE_MIN_WIDTH = 208.dp

/** `.genre-tiles{gap:14px}`. */
private val GENRE_TILE_GAP = 14.dp

/**
 * Every genre the library holds, as its own page of tiles — a Compose port
 * of `utility-pages.js#renderGenres`. Columns come from the window's width
 * the way the web's `auto-fill` does, not from the poster wall's count: a
 * 16:9 tile is a different shape from a plate.
 */
@Composable
internal fun GenresIndexScreen(
    genres: List<GenreIndexEntry>,
    onOpenGenre: (String) -> Unit,
) {
    if (genres.isEmpty()) {
        CenteredMessage("Nothing in the library has a genre recorded.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GENRE_TILE_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(GENRE_TILE_GAP),
        verticalArrangement = Arrangement.spacedBy(GENRE_TILE_GAP),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            ShelfHead(title = "Genres", sub = spelledCountOf(genres.size, "genre"))
        }
        items(items = genres, key = GenreIndexEntry::name) { entry ->
            ArtTile(
                name = entry.name,
                meta = spelledCountOf(entry.count, "title"),
                art = entry.art,
                aspectRatio = GENRE_TILE_ASPECT,
                onClick = { onOpenGenre(entry.name) },
            )
        }
    }
}
