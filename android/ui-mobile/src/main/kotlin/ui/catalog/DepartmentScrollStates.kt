package ui.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * One hoisted scroll position per department that can draw a hero — kept
 * beside Home's own `homeListState`, at the same [ui.LibraryBranches] level,
 * so a tab switch never loses where a viewer scrolled to, and so the
 * departments bar can read whichever one is the active tab's for its own
 * over-hero blend (`ui.chrome.HeroListState`). Series and Tutorials each get
 * their own — switching between those two tabs must not share a position —
 * and Series/Tutorials use a grid, Movies/Documentaries a plain column.
 */
internal class DepartmentScrollStates(
    val movies: LazyListState,
    val series: LazyGridState,
    val tutorials: LazyGridState,
    val documentaries: LazyListState,
)

@Composable
internal fun rememberDepartmentScrollStates(): DepartmentScrollStates {
    val movies = rememberLazyListState()
    val series = rememberLazyGridState()
    val tutorials = rememberLazyGridState()
    val documentaries = rememberLazyListState()
    // The four states are each already remembered; the wrapper around them
    // was not, so a caller that keys its own `remember` on this whole
    // object (`ui.LibraryBranches`'s own `deptScroll`) saw a new instance —
    // and so a changed key — on every recomposition, not only when a state
    // actually changed.
    return remember(movies, series, tutorials, documentaries) {
        DepartmentScrollStates(movies, series, tutorials, documentaries)
    }
}
