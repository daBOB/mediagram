package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.HomeRow
import catalog.KeptKind
import catalog.MagazineHome
import catalog.Shelf
import catalog.everyFilm
import catalog.franchisesIn
import model.MediaSet
import ui.RailItem

/**
 * What shows below the bar once a tab is chosen — Home, a shelf's wall (or
 * its department front page), Collections, or one of the two kept walls —
 * [TvCatalogScreen]'s own content, split out so that composable reads as
 * "which tab, and what the chrome does", not also every screen a tab can
 * open.
 */
@Composable
internal fun TvCatalogBody(
    state: CatalogUiState,
    ready: CatalogUiState.Ready?,
    shelves: List<Shelf>,
    byId: Map<String, MediaSet>,
    tabs: CatalogTabs,
    selected: Int,
    collectionsIndex: Int,
    wallKey: String?,
    railActive: RailItem?,
    railRowFocus: Map<RailItem, FocusRequester>,
    homeListState: LazyListState,
    homeMagazine: MagazineHome?,
    homeRows: List<HomeRow>,
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenFranchise: (id: Long) -> Unit,
    onOpenMoviesPage: () -> Unit,
    onFinish: (setId: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    choose: (Int) -> Unit,
) {
    when {
        state is CatalogUiState.Loading -> TvCenteredMessage("Loading your library…")
        state is CatalogUiState.KidsEmpty -> TvCenteredMessage("Nothing rated FSK 12 or under yet.")
        state is CatalogUiState.Failed -> TvCenteredMessage(state.message)
        ready == null -> TvCenteredMessage("The library is empty.")
        selected == 0 -> {
            // Computed by the caller, not here: the departments bar above
            // reads the same magazine's own cover to decide whether it has
            // anything to bleed under in the first place — a second,
            // independent `magazineHomeOf` call here could read a different
            // `now` and disagree with it on the rare tie that falls right on
            // a day boundary.
            TvHome(
                magazine = requireNotNull(homeMagazine) { "Home selected with no magazine computed for it" },
                rows = homeRows,
                watch = ready.watch,
                listState = homeListState,
                onPlay = onPlay,
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onToggleWatchlist = onToggleWatchlist,
                onSeeAll = { shelf -> choose(tabs.titles.indexOf(shelf).coerceAtLeast(0)) },
                restoreKey = wallKey,
            )
        }
        selected < tabs.firstKept -> {
            val shelf = shelves[selected - 1]
            DepartmentOrShelfWall(
                shelf = shelf,
                watch = ready.watch,
                heldIds = ready.heldIds,
                byId = byId,
                onOpenTitle = onOpenTitle,
                onPlay = onPlay,
                onOpenCollection = onOpenCollection,
                onOpenGenre = onOpenGenre,
                onOpenMoviesPage = onOpenMoviesPage,
                restoreKey = wallKey,
            )
        }
        selected == collectionsIndex -> {
            val movies = remember(shelves) { everyFilm(shelves) }
            TvCollectionsPage(
                franchises = remember(movies) { franchisesIn(movies) },
                lists = ready.watch.collections,
                onOpenFranchise = onOpenFranchise,
                onOpenList = onOpenList,
                onCreateList = onCreateList,
                restoreKey = wallKey,
            )
        }
        else -> {
            TvKeptTab(
                kind = KeptKind.entries[selected - tabs.firstKept],
                shelves = shelves,
                watch = ready.watch,
                onOpenTitle = onOpenTitle,
                onOpenList = onOpenList,
                onCreateList = onCreateList,
                // My List and Continue watching have no pill of their own
                // (the rail chooses either directly), so a wall that
                // empties under the viewer falls back to its own rail row
                // rather than to a pill that was never selected in the
                // first place.
                tabFocus = railRowFocus.getValue(railActive ?: RailItem.MY_LIST),
                restoreKey = wallKey,
                heldIds = ready.heldIds,
                onFinish = onFinish,
            )
        }
    }
}
