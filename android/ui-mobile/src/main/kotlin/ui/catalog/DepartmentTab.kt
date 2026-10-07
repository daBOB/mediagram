package ui.catalog

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.CatalogUiState
import catalog.Department
import catalog.Entry
import catalog.Shelf
import catalog.allSetsById
import catalog.moviesDepartmentOf
import catalog.showsDepartmentOf
import model.Kind
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo

/**
 * A department pill's own page: Movies, Series and Tutorials on their
 * department screens ([MoviesDepartmentScreen], [ShowsDepartmentScreen]),
 * Anime and Documentaries on theirs. Chosen by [Shelf.department], so a
 * department the library gains does not compile until it has a page here.
 */
@Composable
internal fun DepartmentTab(
    shelf: Shelf,
    state: CatalogUiState.Ready,
    columns: Int,
    deptScroll: DepartmentScrollStates,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onOpenMoviesPage: () -> Unit,
    onPlay: (setId: String) -> Unit,
    titleInfo: suspend (String) -> TitleInfo?,
) {
    when (shelf.department) {
        Department.MOVIES -> {
            val films = remember(shelf) { filmsOf(shelf) }
            val department = remember(films, state.watch) { moviesDepartmentOf(films) { id -> state.watch.watched.any { it.setId == id } } }
            department?.let {
                MoviesDepartmentScreen(
                    department = it,
                    films = films,
                    watch = state.watch,
                    onOpenTitle = onOpenTitle,
                    onOpenGenre = onOpenGenre,
                    onOpenGenresIndex = onOpenGenresIndex,
                    onOpenLatest = onOpenLatest,
                    onSeeAllFilms = onOpenMoviesPage,
                    onPlay = onPlay,
                    titleInfo = titleInfo,
                    state = deptScroll.movies,
                )
            }
        }
        Department.SERIES -> ShowsDepartmentTab(Department.SERIES, shelf, state, columns, onOpenTitle, onOpenCollection, deptScroll.series, onPlay)
        Department.ANIME -> AnimeDepartmentTab(shelf, state, columns, onOpenTitle, onOpenCollection, deptScroll.anime, onPlay)
        Department.TUTORIALS -> ShowsDepartmentTab(Department.TUTORIALS, shelf, state, columns, onOpenTitle, onOpenCollection, deptScroll.tutorials, onPlay)
        Department.DOCUMENTARIES -> DocumentariesDepartmentTab(shelf, state, onOpenCollection, deptScroll.documentaries, onPlay)
    }
}

/** Series or Tutorials as its own tab — [dept] says which, and so which kind of set its shows are made of. */
@Composable
private fun ShowsDepartmentTab(
    dept: Department,
    shelf: Shelf,
    state: CatalogUiState.Ready,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    listState: LazyGridState,
    onPlay: (String) -> Unit,
) {
    val shows = remember(shelf) { shelf.entries.filterIsInstance<Entry.Collection>() }
    val byId = remember(state.shelves) { allSetsById(state.shelves) }
    val kind = if (dept == Department.TUTORIALS) Kind.TUTORIAL else Kind.EPISODE
    val department = remember(shows, byId, state.watch) { showsDepartmentOf(kind, shows, byId, state.watch) }
    department?.let {
        ShowsDepartmentScreen(dept, it, state.watch, state.heldIds, columns, onOpenTitle, onOpenCollection, onPlay, listState)
    }
}
