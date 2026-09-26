package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.Entry
import catalog.KeptKind
import catalog.Shelf
import catalog.franchisesIn
import catalog.homeRowsOf
import catalog.magazineHomeOf
import model.MediaSet

/**
 * What shows below the masthead once a tab is chosen — Home, a shelf's wall
 * (or its department front page), Collections, or one of the kept walls —
 * [TvCatalogScreen]'s own content, split out so that composable reads as
 * "which tab, and what the masthead does", not also every screen a tab can
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
    mastheadSelected: Int,
    selectedTabFocus: FocusRequester,
    mastheadFocus: FocusRequester,
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
            val movies =
                remember(shelves) {
                    shelves.firstOrNull { it.title == "Movies" }?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }
                }
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
                // Continue and Watchlist have no masthead tab of their own
                // any more (reached from the overflow menu instead), so
                // `selectedTabFocus` — attached only to whichever tab the
                // masthead itself currently marks selected — is never
                // claimed while one of them is showing, and asking it to
                // take focus would find nothing to land on. `mastheadFocus`
                // is the row's own requester, always attached, so it is
                // what a wall that empties under the viewer falls back to
                // instead.
                tabFocus = if (mastheadSelected >= 0) selectedTabFocus else mastheadFocus,
                restoreKey = wallKey,
                heldIds = ready.heldIds,
                onFinish = onFinish,
            )
        }
    }
}
