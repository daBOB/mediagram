package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import catalog.CatalogUiState
import catalog.Entry
import catalog.GenreShelf
import catalog.genreShelf
import catalog.keyOf
import model.WatchSnapshot
import ui.tv.TvSafeArea

/**
 * Everything tagged with one genre, films first, then series — the
 * television twin of the phone's `GenreScreen` and the web's `viewGenre`,
 * over the same [genreShelf]. There is no shelf of these on Home on any
 * surface; a genre is only ever reached from a title that carries it.
 *
 * Shown as loading rather than as "nothing tagged" while [catalogState] has
 * not read the shelves yet — a restore landing here before the catalogue is
 * back is not yet an answer, and saying so would tell a viewer their genre
 * lost every title in it.
 */
@Composable
internal fun TvGenre(
    name: String,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String?,
) {
    if (catalogState !is CatalogUiState.Ready) {
        GenreMessage("Loading your library…")
        return
    }
    val shelf = remember(catalogState.shelves, name) { genreShelf(catalogState.shelves, name) }
    TvGenreWall(name, shelf, watch, onOpenTitle, onOpenCollection, restoreKey)
}

/**
 * The wall itself: a plate per film and per show, as every other wall
 * draws them, under the web's "Name · n" heading. Movies and Series are
 * labelled only when both are there — a wall of one kind already says what
 * it is by what is on it. Coming back lands on the plate [restoreKey] names.
 */
@Composable
internal fun TvGenreWall(
    name: String,
    shelf: GenreShelf,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String?,
) {
    val entries: List<Entry> = remember(shelf) { shelf.films + shelf.series }
    if (entries.isEmpty()) {
        GenreMessage("Nothing in the library is tagged with this genre.")
        return
    }
    val headings =
        remember(shelf) {
            if (shelf.films.isNotEmpty() && shelf.series.isNotEmpty()) mapOf(0 to "Movies", shelf.films.size to "Series") else emptyMap()
        }
    val (positions, watchedIds) = rememberWatchMarks(watch)
    TvPage {
        TvWall(
            items = entries,
            key = ::keyOf,
            restoreKey = restoreKey,
            onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
            header = { TvCountedHeading(name, entries.size) },
            headings = headings,
            plate = { entry, modifier, onOpen ->
                TvEntryPlate(entry = entry, positions = positions, watchedIds = watchedIds, onOpen = onOpen, modifier = modifier)
            },
        )
    }
}

/**
 * The phone's centred sentence, as a stop the remote rests on: a genre page
 * with nothing to open would otherwise leave the remote on nothing at all,
 * with only Back — which still leaves to the title — to tell it apart from
 * a frozen screen.
 */
@Composable
private fun GenreMessage(text: String) {
    val focus = remember { FocusRequester() }
    TvSafeArea {
        TvReadableParagraph(text, modifier = Modifier.focusRequester(focus))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
