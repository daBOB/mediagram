package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * One hoisted scroll position per department that draws a hero, kept beside
 * Home's own `homeListState` at [TvCatalogScreen]'s own level — the
 * television twin of the tablet's `ui.catalog.DepartmentScrollStates` — so a
 * tab switch never loses where a viewer scrolled to, and so the departments
 * bar can read whichever one is the active tab's for its own over-hero
 * blend ([ui.chrome.HeroListState]). Series, Anime and Tutorials use a grid
 * ([TvWall]); Movies and Documentaries a plain column.
 */
internal class TvDepartmentScrollStates(
    val movies: LazyListState,
    val series: LazyGridState,
    val anime: LazyGridState,
    val tutorials: LazyGridState,
    val documentaries: LazyListState,
)

@Composable
internal fun rememberTvDepartmentScrollStates(): TvDepartmentScrollStates {
    val movies = rememberLazyListState()
    val series = rememberLazyGridState()
    val anime = rememberLazyGridState()
    val tutorials = rememberLazyGridState()
    val documentaries = rememberLazyListState()
    // Every one of the five is already remembered; wrapping them was not —
    // a caller keying its own `remember` on this whole object would
    // otherwise see a new instance, and so a changed key, on every
    // recomposition rather than only when a state actually changed.
    return remember(movies, series, anime, tutorials, documentaries) {
        TvDepartmentScrollStates(movies, series, anime, tutorials, documentaries)
    }
}
