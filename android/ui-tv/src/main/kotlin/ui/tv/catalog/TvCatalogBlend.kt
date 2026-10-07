package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import catalog.CatalogTab
import catalog.Department
import catalog.Shelf
import catalog.heroArtOf
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Overscan
import model.MediaSet
import model.WatchSnapshot
import ui.common.catalog.DepartmentScrollStates
import ui.common.chrome.asHeroListState
import ui.common.chrome.coverBlend
import ui.tv.chrome.TvDepartmentsBarHeight

/**
 * How solid [TvLibraryChrome]'s own bar should read over whatever the
 * selected tab draws under it — Home's own cover, read live through
 * [homeListState] the way [ui.common.chrome.HeroListState] already does for the
 * tablet's own hero pages, or the selected department's own hero, read
 * through [deptScroll] at its own fixed height ([TvDepartmentHeroHeight] —
 * unlike Home's cover, no department hero ever measures differently).
 * Opaque (`1f`) on every other tab, and on a department with nothing to
 * lead its own hero with, or with [Backdrop.SOLID] hiding the art that
 * would otherwise be there.
 */
@Composable
internal fun rememberTvCatalogBlend(
    selected: CatalogTab,
    shelves: List<Shelf>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot?,
    homeListState: LazyListState,
    homeHasCover: Boolean,
    deptScroll: DepartmentScrollStates,
): Float {
    // The department this tab is (`null` on Home, a kept wall or
    // Collections) — the one thing [heroArtOf] and [deptScroll] both need to
    // tell whether *this* tab's own hero, not Home's cover, is what the bar
    // should bleed under.
    val department = (selected as? CatalogTab.Dept)?.department
    val soldOut = LocalBackdrop.current == Backdrop.SOLID
    val hasHeroArt =
        remember(shelves, byId, watch, department, soldOut) {
            !soldOut && department != null && heroArtOf(department, shelves, byId, watch ?: WatchSnapshot.Empty) != null
        }
    val density = LocalDensity.current
    val barHeightPx = remember(density) { with(density) { (TvDepartmentsBarHeight + Overscan.vertical).toPx() } }
    val deptHeroHeightPx = remember(density) { with(density) { TvDepartmentHeroHeight.toPx() } }
    val blend by remember(selected, homeHasCover, hasHeroArt) {
        derivedStateOf {
            // A department's own hero is always this one fixed height —
            // unlike Home's cover, nothing here ever measures it live.
            val deptPosition: Pair<Int, Int>? =
                if (!hasHeroArt) {
                    null
                } else {
                    when (department) {
                        Department.MOVIES -> deptScroll.movies.firstVisibleItemIndex to deptScroll.movies.firstVisibleItemScrollOffset
                        Department.SERIES -> deptScroll.series.firstVisibleItemIndex to deptScroll.series.firstVisibleItemScrollOffset
                        Department.ANIME -> deptScroll.anime.firstVisibleItemIndex to deptScroll.anime.firstVisibleItemScrollOffset
                        Department.TUTORIALS -> deptScroll.tutorials.firstVisibleItemIndex to deptScroll.tutorials.firstVisibleItemScrollOffset
                        Department.DOCUMENTARIES -> deptScroll.documentaries.firstVisibleItemIndex to deptScroll.documentaries.firstVisibleItemScrollOffset
                        null -> null
                    }
                }
            when {
                selected == CatalogTab.Home && homeHasCover -> {
                    val hero = homeListState.asHeroListState()
                    coverBlend(hero.firstVisibleItemIndex, hero.firstVisibleItemScrollOffset, hero.heroHeightPx, barHeightPx)
                }
                deptPosition != null -> coverBlend(deptPosition.first, deptPosition.second, deptHeroHeightPx, barHeightPx)
                else -> 1f
            }
        }
    }
    return blend
}
