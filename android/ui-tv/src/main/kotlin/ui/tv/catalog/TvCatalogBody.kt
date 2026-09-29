package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.KeptKind
import catalog.Shelf
import catalog.everyFilm
import catalog.franchisesIn
import catalog.homeRowsOf
import catalog.magazineHomeOf
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
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenFranchise: (id: Long) -> Unit,
    onOpenMoviesPage: () -> Unit,
    onFinish: (setId: String) -> Unit,
    choose: (Int) -> Unit,
) {
    when {
        state is CatalogUiState.Loading -> TvCenteredMessage("Loading your library…")
        state is CatalogUiState.KidsEmpty -> TvCenteredMessage("Nothing rated FSK 12 or under yet.")
        state is CatalogUiState.Failed -> TvCenteredMessage(state.message)
        ready == null -> TvCenteredMessage("The library is empty.")
        selected == 0 -> {
            TvHome(
                rows =
                    remember(shelves, ready.watch, ready.heldIds) {
                        homeRowsOf(shelves, ready.watch, ready.heldIds).filterNot { it.title == "Latest films" }
                    },
                watch = ready.watch,
                onOpenTitle = onOpenTitle,
                onPlay = onPlay,
                onOpenCollection = onOpenCollection,
                onSeeAll = { shelf -> choose(tabs.titles.indexOf(shelf).coerceAtLeast(0)) },
                restoreKey = wallKey,
                // The magazine header already carries its own "Recently
                // added" row over the Movies shelf — dropping "Latest films"
                // above is what keeps Home from showing the same films twice.
                magazine =
                    remember(shelves, ready.watch, ready.heldIds) {
                        magazineHomeOf(
                            shelves,
                            ready.watch,
                            editorsChoice = ready.watch.editorsChoice,
                            now = System.currentTimeMillis(),
                            heldIds = ready.heldIds,
                        )
                    },
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
