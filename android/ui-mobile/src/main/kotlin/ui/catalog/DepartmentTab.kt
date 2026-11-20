package ui.catalog

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.CatalogUiState
import catalog.Department
import catalog.Shelf
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo

/**
 * A department pill's own page: Movies, Series and Tutorials on their
 * department screens ([MoviesDepartmentScreen], [ShowsDepartmentScreen]),
 * Anime, Documentaries and Audiobooks on theirs, each read from the state's pages.
 * Chosen by [Shelf.department], so a department the library gains does not
 * compile until it has a page here.
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
            state.pages.movies?.let {
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
        Department.SERIES -> ShowsDepartmentTab(Department.SERIES, state, columns, onOpenTitle, onOpenCollection, deptScroll.series, onPlay)
        Department.ANIME -> AnimeDepartmentTab(state, columns, onOpenTitle, onOpenCollection, deptScroll.anime, onPlay)
        Department.TUTORIALS -> ShowsDepartmentTab(Department.TUTORIALS, state, columns, onOpenTitle, onOpenCollection, deptScroll.tutorials, onPlay)
        Department.DOCUMENTARIES -> DocumentariesDepartmentTab(state, onOpenCollection, deptScroll.documentaries, onPlay)
        Department.AUDIOBOOKS -> AudiobooksDepartmentTab(state, columns, deptScroll.audiobooks, onPlay)
    }
}

/** Series or Tutorials as its own tab — [dept] says which, and so which kind of set its shows are made of. */
@Composable
private fun ShowsDepartmentTab(
    dept: Department,
    state: CatalogUiState.Ready,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    listState: LazyGridState,
    onPlay: (String) -> Unit,
) {
    val department = if (dept == Department.TUTORIALS) state.pages.tutorials else state.pages.series
    department?.let {
        ShowsDepartmentScreen(dept, it, state.watch, state.heldIds, columns, onOpenTitle, onOpenCollection, onPlay, listState)
    }
}
