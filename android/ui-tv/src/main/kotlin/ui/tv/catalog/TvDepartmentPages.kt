package ui.tv.catalog

import androidx.compose.runtime.Composable
import catalog.Department
import catalog.Entry
import catalog.LibraryPages
import catalog.Shelf
import model.MediaSet
import model.WatchSnapshot
import ui.common.catalog.DepartmentScrollStates

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
 * [TvShowsDepartmentPage], Anime and Documentaries theirs, chosen by
 * [Shelf.department] and read from [pages]. A department without a page —
 * an empty shelf — falls back to the plain wall, the same "nothing to show"
 * this surface already draws for every other empty state: a department is a
 * richer *front page*, never a replacement for the shelf itself.
 *
 * No size floor of its own here — the web's `renderMoviesDept`/`renderShowsDept`
 * always draw the department page for a non-empty shelf; Movies' own rows
 * (Featured, Genres, Acclaimed, Recently added) show whenever the page
 * answers something for them, and `showsDepartmentOf` is where Popular/New
 * episodes already gate themselves past a dozen shows — this composable adds
 * no gate of its own on top of that.
 */
@Composable
internal fun DepartmentOrShelfWall(
    shelf: Shelf,
    pages: LibraryPages,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    deptScroll: DepartmentScrollStates,
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenMoviesPage: () -> Unit,
    restoreKey: String?,
) {
    when (shelf.department) {
        Department.MOVIES -> pages.movies?.let { dept ->
            TvMoviesDepartmentPage(dept, onOpenTitle, onPlay, onOpenGenre, onOpenMoviesPage, restoreKey, heldIds, deptScroll.movies)
            return
        }
        Department.ANIME -> pages.anime?.let { dept ->
            TvAnimeDepartmentPage(dept, watch, onOpenTitle, onOpenCollection, onPlay, restoreKey, heldIds, deptScroll.anime)
            return
        }
        Department.DOCUMENTARIES -> {
            // Unlike every other department, Documentaries is never itself
            // omitted from the shelf list for being empty (its pill still
            // reads "0"), so this is the one department whose own empty state
            // actually has to be drawn rather than never reached.
            if (shelf.entries.isEmpty()) {
                TvCenteredMessage("No documentaries yet. Upload one with mediagram add-docu <file|folder>.")
                return
            }
            pages.documentaries?.let { dept ->
                TvDocumentariesDepartmentPage(dept, watch, onOpenCollection, onPlay, restoreKey, heldIds, deptScroll.documentaries)
                return
            }
        }
        Department.SERIES -> pages.series?.let { dept ->
            TvShowsDepartmentPage(shelf.department, dept, watch, onOpenTitle, onOpenCollection, onPlay, restoreKey, heldIds, deptScroll.series)
            return
        }
        Department.TUTORIALS -> pages.tutorials?.let { dept ->
            TvShowsDepartmentPage(shelf.department, dept, watch, onOpenTitle, onOpenCollection, onPlay, restoreKey, heldIds, deptScroll.tutorials)
            return
        }
    }
    TvShelfWall(shelf, watch, onOpenTitle, onOpenCollection, restoreKey, heldIds)
}
