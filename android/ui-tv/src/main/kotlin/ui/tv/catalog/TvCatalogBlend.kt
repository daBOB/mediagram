package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import catalog.ANIME
import catalog.CatalogTabs
import catalog.DOCUMENTARIES
import catalog.Shelf
import catalog.heroArtOf
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Overscan
import model.MediaSet
import model.WatchSnapshot
import ui.chrome.asHeroListState
import ui.chrome.coverBlend
import ui.tv.chrome.TvDepartmentsBarHeight

/**
 * How solid [TvLibraryChrome]'s own bar should read over whatever the
 * selected tab draws under it — Home's own cover, read live through
 * [homeListState] the way [ui.chrome.HeroListState] already does for the
 * tablet's own hero pages, or the selected department's own hero, read
 * through [deptScroll] at its own fixed height ([TvDepartmentHeroHeight] —
 * unlike Home's cover, no department hero ever measures differently).
 * Opaque (`1f`) on every other tab, and on a department with nothing to
 * lead its own hero with, or with [Backdrop.SOLID] hiding the art that
 * would otherwise be there.
 */
@Composable
internal fun rememberTvCatalogBlend(
    selected: Int,
    tabs: CatalogTabs,
    shelves: List<Shelf>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot?,
    homeListState: LazyListState,
    homeHasCover: Boolean,
    deptScroll: TvDepartmentScrollStates,
): Float {
    // The department this tab is (`null` on Home, a kept wall, Collections,
    // or a plain shelf with no front page) — the one thing [heroArtOf] and
    // [deptScroll] both need to tell whether *this* tab's own hero, not
    // Home's cover, is what the bar should bleed under.
    val activeShelfTitle = remember(shelves, selected, tabs) { shelves.getOrNull(selected - 1)?.title.takeIf { selected in 1 until tabs.firstKept } }
    val soldOut = LocalBackdrop.current == Backdrop.SOLID
    val hasHeroArt =
        remember(shelves, byId, watch, activeShelfTitle, soldOut) {
            !soldOut && activeShelfTitle != null && heroArtOf(activeShelfTitle, shelves, byId, watch ?: WatchSnapshot.Empty) != null
        }
    val density = LocalDensity.current
    val barHeightPx = remember(density) { with(density) { (TvDepartmentsBarHeight + Overscan.vertical).toPx() } }
    val deptHeroHeightPx = remember(density) { with(density) { TvDepartmentHeroHeight.toPx() } }
    val blend by remember(homeHasCover, activeShelfTitle, hasHeroArt) {
        derivedStateOf {
            // A department's own hero is always this one fixed height —
            // unlike Home's cover, nothing here ever measures it live.
            val deptPosition: Pair<Int, Int>? =
                if (!hasHeroArt) {
                    null
                } else {
                    when (activeShelfTitle) {
                        "Movies" -> deptScroll.movies.firstVisibleItemIndex to deptScroll.movies.firstVisibleItemScrollOffset
                        "Series" -> deptScroll.series.firstVisibleItemIndex to deptScroll.series.firstVisibleItemScrollOffset
                        ANIME -> deptScroll.anime.firstVisibleItemIndex to deptScroll.anime.firstVisibleItemScrollOffset
                        "Tutorials" -> deptScroll.tutorials.firstVisibleItemIndex to deptScroll.tutorials.firstVisibleItemScrollOffset
                        DOCUMENTARIES -> deptScroll.documentaries.firstVisibleItemIndex to deptScroll.documentaries.firstVisibleItemScrollOffset
                        else -> null
                    }
                }
            when {
                selected == 0 && homeHasCover -> {
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
