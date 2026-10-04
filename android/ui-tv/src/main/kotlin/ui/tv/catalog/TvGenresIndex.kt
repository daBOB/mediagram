package ui.tv.catalog

import androidx.compose.runtime.Composable
import catalog.GenreIndexEntry
import catalog.spelledCountOf
import ui.catalog.GENRE_TILE_ASPECT

/**
 * Four tiles across: at a pushed frame's 864dp between the overscan
 * margins, that is about 205dp a tile — within a few dp of the web's own
 * 13rem floor (`.genre-tiles`, `catalog.css`), the width it would choose
 * here itself.
 */
private const val GenreTileColumns = 4

/**
 * Every genre the library holds, most titles first — the television twin of
 * the web's `utility-pages.js#renderGenres`: a 16:9 tile per genre with its
 * name across its own most popular title's art, opening the same [TvGenre]
 * page a title's own genre link opens.
 */
@Composable
internal fun TvGenresIndex(
    genres: List<GenreIndexEntry>,
    onOpenGenre: (name: String) -> Unit,
    restoreKey: String? = null,
) {
    if (genres.isEmpty()) {
        GenreMessage("Nothing in the library has a genre recorded.")
        return
    }
    TvPage {
        TvWall(
            items = genres,
            key = GenreIndexEntry::name,
            restoreKey = restoreKey,
            onOpen = { genre -> onOpenGenre(genre.name) },
            header = { TvShelfHead("Genres", spelledCountOf(genres.size, "genre")) },
            columns = GenreTileColumns,
            plate = { genre, modifier, onOpen ->
                TvArtTile(
                    name = genre.name,
                    meta = spelledCountOf(genre.count, "title"),
                    art = genre.art,
                    aspectRatio = GENRE_TILE_ASPECT,
                    onOpen = onOpen,
                    modifier = modifier,
                )
            },
        )
    }
}
