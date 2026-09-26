package ui.catalog

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.ShelfViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CatalogTabs
import catalog.CatalogUiState
import catalog.Entry
import catalog.KeptKind
import catalog.Shelf
import catalog.allSetsById
import catalog.catalogTabsOf
import catalog.continueWall
import catalog.franchisesIn
import catalog.homeRowsOf
import catalog.magazineHomeOf
import catalog.moviesDepartmentOf
import catalog.showsDepartmentOf
import catalog.watchlistWall
import designsystem.Spacing
import model.Kind
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * Which of [tabs]'s own, full index space (Home, each shelf, then Continue,
 * Watchlist and Collections) the masthead draws — every index except
 * Continue and Watchlist, which moved to the overflow menu's own utilities
 * (`ui.BrowseActions`) and land on the same index they always had rather
 * than needing a tab of their own; Collections keeps its own, now
 * department, slot at the end.
 */
internal fun visibleTabIndices(tabs: CatalogTabs): List<Int> {
    val firstKept = tabs.firstKept
    return tabs.titles.indices.filterNot { it == firstKept || it == firstKept + 1 }
}

/**
 * The shelves, and one line above them while the library is being worked
 * on. [fetching] is the other run that changes what is on these shelves —
 * it fills in the artwork on them, and the descriptions behind them — and
 * it is reported here rather than beside itself, because a viewer watching
 * something happen should not have to learn a second vocabulary for it
 * depending on which menu item started it.
 *
 * [chosenTab]/[onTabChange] name a position over [catalogTabsOf]'s own,
 * full index space — Home, each shelf, then Continue, Watchlist and
 * Collections — even though the masthead itself (see [ShelfTabs]) draws
 * only the departments: Continue and Watchlist moved to the overflow menu's
 * own utilities (`ui.BrowseActions`), and jump here to the same index they
 * always had rather than needing a frame of their own.
 */
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    fetching: Boolean,
    chosenTab: Int,
    onTabChange: (Int) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onOpenMoviesPage: () -> Unit,
    onOpenFranchise: (Long) -> Unit,
    /** Starts a title with an explicit run, or none — the Featured reel's Play. */
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    /** Continue's "Mark finished". */
    onFinish: (setId: String) -> Unit,
    /** What the index says about a title — the Featured reel's score and tagline. */
    titleInfo: suspend (String) -> TitleInfo? = { null },
) {
    when (state) {
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
        CatalogUiState.Empty -> CenteredMessage("The library is empty.")
        CatalogUiState.KidsEmpty -> CenteredMessage("Nothing rated FSK 12 or under yet.")
        is CatalogUiState.Failed -> CenteredMessage(state.message)
        is CatalogUiState.Ready -> Shelves(
            state, fetching, chosenTab, onTabChange, onOpenTitle, onOpenCollection, onOpenList, onCreateList,
            onOpenGenre, onOpenGenresIndex, onOpenLatest, onOpenMoviesPage, onOpenFranchise, onPlayRun, onFinish, titleInfo,
        )
    }
}

/**
 * One department on screen, chosen from the masthead above it — Home, then
 * Movies, Series and Tutorials as their own department pages (see
 * [MoviesDepartmentScreen], [ShowsDepartmentScreen]), then Collections
 * (see [CollectionsScreen]). Continue and Watchlist still draw with
 * [KeptWall], reached from the overflow menu rather than a visible tab.
 */
@Composable
private fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    chosenTab: Int,
    onTabChange: (Int) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onOpenMoviesPage: () -> Unit,
    onOpenFranchise: (Long) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    onFinish: (setId: String) -> Unit,
    titleInfo: suspend (String) -> TitleInfo?,
) {
    val shelfViewModel: ShelfViewModel = hiltViewModel()
    val chosenView by shelfViewModel.view.collectAsStateWithLifecycle()
    val shelfView = ShelfViewChoice(chosenView, shelfViewModel::choose)
    val shelves = state.shelves
    if (shelves.isEmpty()) {
        CenteredMessage("The library is empty.")
        return
    }
    val fullTabs = remember(shelves) { catalogTabsOf(shelves) }
    val firstKept = fullTabs.firstKept
    val visible = remember(fullTabs) { visibleTabIndices(fullTabs) }
    val selected = chosenTab.coerceIn(0, fullTabs.titles.lastIndex)
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ShelfTabs(
            titles = visible.map(fullTabs.titles::get),
            selected = visible.indexOf(selected).coerceAtLeast(0),
            firstKeptIndex = visible.size,
            onSelect = { position -> onTabChange(visible[position]) },
        )
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
            )
        }
        when {
            selected == 0 -> HomeScreen(
                magazine = remember(shelves, state.watch, state.heldIds) {
                    magazineHomeOf(shelves, state.watch, editorsChoice = state.watch.editorsChoice, now = System.currentTimeMillis(), heldIds = state.heldIds)
                },
                rows = remember(shelves, state.watch, state.heldIds) {
                    homeRowsOf(shelves, state.watch, state.heldIds).filterNot { it.title in setOf("Continue", "Next up", "Latest films") }
                },
                watch = state.watch,
                columns = columns,
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onSeeAll = { shelf -> onTabChange(fullTabs.titles.indexOf(shelf).coerceAtLeast(0)) },
            )

            selected == firstKept -> KeptWall(KeptKind.CONTINUE, continueWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds, onFinish)
            selected == firstKept + 1 -> KeptWall(KeptKind.WATCHLIST, watchlistWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds)

            selected == fullTabs.titles.lastIndex -> {
                val movies = remember(shelves) { shelves.firstOrNull { it.title == "Movies" }?.let(::filmsOf).orEmpty() }
                CollectionsScreen(
                    franchises = remember(movies) { franchisesIn(movies) },
                    lists = state.watch.collections,
                    onOpenFranchise = onOpenFranchise,
                    onOpenList = onOpenList,
                    onCreateList = onCreateList,
                )
            }

            else -> {
                val shelf = shelves[selected - 1]
                when (shelf.title) {
                    "Movies" -> {
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
                                onPlay = { id -> onPlayRun(id, emptyList()) },
                                titleInfo = titleInfo,
                            )
                        }
                    }
                    "Series" -> ShowsDepartment(Kind.EPISODE, "Series", "episode", shelf, state, columns, onOpenTitle, onOpenCollection)
                    "Tutorials" -> ShowsDepartment(Kind.TUTORIAL, "Tutorials", "lesson", shelf, state, columns, onOpenTitle, onOpenCollection)
                    else -> ShelfWall(shelf, state.watch, state.heldIds, columns, shelfView, onOpenTitle, onOpenCollection)
                }
            }
        }
    }
}

@Composable
private fun ShowsDepartment(
    kind: Kind,
    label: String,
    unit: String,
    shelf: Shelf,
    state: CatalogUiState.Ready,
    columns: Int,
    onOpenTitle: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    val shows = remember(shelf) { shelf.entries.filterIsInstance<Entry.Collection>() }
    val byId = remember(state.shelves) { allSetsById(state.shelves) }
    val department = remember(shows, byId, state.watch) { showsDepartmentOf(kind, shows, byId, state.watch) }
    department?.let {
        ShowsDepartmentScreen(label, unit, it, state.watch, state.heldIds, columns, onOpenTitle, onOpenCollection)
    }
}
