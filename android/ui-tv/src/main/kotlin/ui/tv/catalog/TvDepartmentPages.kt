package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.Entry
import catalog.Shelf
import catalog.moviesDepartmentOf
import catalog.showsDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot

/**
 * Every film the Movies shelf holds, one flat wall — "All N films" from the
 * department's own front page, and the `MOVIES_PAGE` frame's own screen: one
 * lazy-scrolling wall rather than the web's numbered pages, since Android
 * renders the whole of it at once (see `LibraryPositions`'s own doc on
 * `MOVIES_PAGE`).
 */
@Composable
internal fun TvMoviesPage(
    films: List<MediaSet>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    TvPage {
        TvWall(
            items = films,
            key = MediaSet::setId,
            restoreKey = restoreKey,
            onOpen = { set -> onOpenTitle(set.setId) },
            header = { TvCountedHeading("All films", films.size) },
            plate = { set, modifier, onOpen ->
                TvEntryPlate(Entry.Film(set), positions, watchedIds, onOpen, modifier, heldIds)
            },
        )
    }
}

/**
 * A shelf as its own department front page, when it has one built —
 * Movies gets [TvMoviesDepartmentPage], Series and Tutorials get
 * [TvShowsDepartmentPage] (told apart by [Shelf.title] the way the masthead
 * itself names them). Every department view model answers `null` for an
 * empty shelf, and this falls back to the plain wall then, the same "nothing
 * to show" this surface already draws for every other empty state — a
 * department is a richer *front page*, never a replacement for the shelf
 * itself.
 *
 * [byId] is every set on any shelf, not just this one's own shows —
 * [showsDepartmentOf]'s own `byId` resolves a *these shows'* progress row to
 * the set it belongs to, and a progress row can name a set of any kind
 * (`catalog.allSetsById`'s own doc).
 *
 * No size floor of its own here — the web's `renderMoviesDept`/`renderShowsDept`
 * always draw the department page for a non-empty shelf; Movies' own rows
 * (Featured, Genres, Acclaimed, Recently added) show whenever the view model
 * answers something for them, and `showsDepartmentOf` is where Popular/New
 * episodes already gate themselves past a dozen shows — this composable adds
 * no gate of its own on top of that.
 */
@Composable
internal fun DepartmentOrShelfWall(
    shelf: Shelf,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    byId: Map<String, MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenMoviesPage: () -> Unit,
    restoreKey: String?,
) {
    if (shelf.title == "Movies") {
        val films = remember(shelf) { shelf.entries.filterIsInstance<Entry.Film>().map { it.set } }
        val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
        val dept = remember(films, watchedIds) { moviesDepartmentOf(films) { it in watchedIds } }
        if (dept != null) {
            TvMoviesDepartmentPage(dept, onOpenTitle, onPlay, onOpenGenre, onOpenMoviesPage, restoreKey, heldIds)
            return
        }
    } else {
        val shows = remember(shelf) { shelf.entries.filterIsInstance<Entry.Collection>() }
        val kind = if (shelf.title == "Tutorials") Kind.TUTORIAL else Kind.EPISODE
        val dept = remember(shows, watch, byId) { showsDepartmentOf(kind, shows, byId, watch) }
        if (dept != null) {
            TvShowsDepartmentPage(dept, watch, onOpenTitle, onOpenCollection, restoreKey, heldIds)
            return
        }
    }
    TvShelfWall(shelf, watch, onOpenTitle, onOpenCollection, restoreKey, heldIds)
}
