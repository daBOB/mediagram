package ui.tv.catalog

import androidx.compose.runtime.Composable
import catalog.GenreIndexEntry
import java.io.File

/**
 * Every genre the library holds, most titles first — the television twin of
 * the web's `utility-pages.js#genreIndex` page: a tile per genre, pictured by
 * its own most popular title, opening the same [TvGenre] page a title's own
 * genre link opens.
 */
@Composable
internal fun TvGenresIndex(
    genres: List<GenreIndexEntry>,
    onOpenGenre: (name: String) -> Unit,
    restoreKey: String? = null,
) {
    TvPage {
        TvWall(
            items = genres,
            key = GenreIndexEntry::name,
            restoreKey = restoreKey,
            onOpen = { genre -> onOpenGenre(genre.name) },
            header = { TvCountedHeading("Genres", genres.size) },
            plate = { genre, modifier, onOpen ->
                TvPlate(
                    title = genre.name,
                    posterPath = genre.art?.let(::File),
                    onOpen = onOpen,
                    modifier = modifier,
                    caption = "${genre.count} ${if (genre.count == 1) "title" else "titles"}",
                )
            },
        )
    }
}
