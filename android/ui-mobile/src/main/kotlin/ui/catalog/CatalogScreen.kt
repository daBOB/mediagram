package ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.HOME_POSTER_ROW_LIMIT
import catalog.KeptKind
import catalog.allSetsById
import catalog.continueWall
import catalog.everyFilm
import catalog.franchisesIn
import catalog.hasContent
import catalog.latestOf
import catalog.magazineHomeOf
import catalog.watchlistWall
import designsystem.Spacing
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo
import ui.chrome.LocalTopChrome

/**
 * The shelves, and one line above them while the library is being worked
 * on. [fetching] is the other run that changes what is on these shelves —
 * it fills in the artwork on them, and the descriptions behind them — and
 * it is reported here rather than beside itself, because a viewer watching
 * something happen should not have to learn a second vocabulary for it
 * depending on which menu item started it.
 *
 * [chosenTab] is any [CatalogTab], not only one the departments bar
 * (`ui.chrome.DepartmentsBar`) draws a pill for: Continue and My List are the
 * rail's own rows (`ui.chrome.BrowseActions`) and land here as tabs of their own,
 * rather than needing a frame of their own. [onTabChange] is Home's "See
 * all", which opens the tab its row is a window onto.
 */
@Composable
internal fun CatalogScreen(
    state: CatalogUiState,
    fetching: Boolean,
    chosenTab: CatalogTab,
    onTabChange: (CatalogTab) -> Unit,
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
    /** The cover's "+ My List" pill and every card's own toggle — see [data.WatchStateRepository.setWatchlisted]. */
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    /**
     * Home's own list state, hoisted up to [ui.chrome.LibraryHome] so the
     * departments bar can read where the page actually is — the same
     * instance [magazineHomeOf] below is asked to draw into, not a state of
     * this screen's own that the bar would have no way to reach.
     */
    homeListState: LazyListState,
    /**
     * One list state per department that can draw a hero, hoisted the same
     * way [homeListState] is — [ui.chrome.LibraryHome] reads whichever one
     * is the active tab's for the departments bar's own over-hero blend.
     */
    deptScroll: DepartmentScrollStates,
    /** The magazine's own "now" — shared with whoever needs to know ahead of composing this whether Home has a cover to draw, so the two never pick different editorial sets from two different moments. */
    now: Long,
    /** What the index says about a title — the Featured reel's score and tagline. */
    titleInfo: suspend (String) -> TitleInfo? = { null },
) {
    when (state) {
        CatalogUiState.Loading -> CenteredMessage("Loading your library…")
        CatalogUiState.Empty -> CenteredMessage("The library is empty.")
        is CatalogUiState.KidsEmpty -> CenteredMessage(state.message)
        is CatalogUiState.Failed -> CenteredMessage(state.message)
        is CatalogUiState.Ready -> Shelves(
            state = state, fetching = fetching, chosenTab = chosenTab, onTabChange = onTabChange,
            onOpenTitle = onOpenTitle, onOpenCollection = onOpenCollection, onOpenList = onOpenList,
            onCreateList = onCreateList, onOpenGenre = onOpenGenre, onOpenGenresIndex = onOpenGenresIndex,
            onOpenLatest = onOpenLatest, onOpenMoviesPage = onOpenMoviesPage, onOpenFranchise = onOpenFranchise,
            onPlayRun = onPlayRun, onFinish = onFinish, onToggleWatchlist = onToggleWatchlist,
            homeListState = homeListState, deptScroll = deptScroll, now = now, titleInfo = titleInfo,
        )
    }
}

/**
 * One tab on screen, chosen from the bar above it — Home, then each
 * department as its own page ([DepartmentTab]), then Collections (see
 * [CollectionsScreen]). Continue and My List draw with [KeptWall], reached
 * from the rail's own rows (`ui.chrome.BrowseActions`) rather than a visible pill.
 */
@Composable
private fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    chosenTab: CatalogTab,
    onTabChange: (CatalogTab) -> Unit,
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
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    homeListState: LazyListState,
    deptScroll: DepartmentScrollStates,
    now: Long,
    titleInfo: suspend (String) -> TitleInfo?,
) {
    val shelves = state.shelves
    if (!shelves.hasContent()) {
        CenteredMessage("The library is empty.")
        return
    }
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    // Home, and a department whose own hero drew lead art, both draw under
    // the bar — everything else, and a hero with nothing to lead with, is
    // padded clear of it, the same seam `ui.chrome.LibraryHome` reads to decide which.
    val topChrome = LocalTopChrome.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = topChrome))
        }
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small).padding(top = topChrome),
            )
        }
        when (chosenTab) {
            CatalogTab.Home -> HomeScreen(
                magazine = remember(shelves, state.watch, state.heldIds, now) {
                    magazineHomeOf(
                        shelves, state.watch, editorsChoice = state.watch.editorsChoice, now = now, heldIds = state.heldIds,
                        recentLimit = HOME_POSTER_ROW_LIMIT,
                    )
                },
                latest = remember(shelves) { latestOf(shelves) },
                watch = state.watch,
                listState = homeListState,
                onPlay = { id -> onPlayRun(id, emptyList()) },
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onToggleWatchlist = onToggleWatchlist,
                onSeeAll = onTabChange,
            )

            is CatalogTab.Kept -> when (chosenTab.kind) {
                KeptKind.CONTINUE -> KeptWall(KeptKind.CONTINUE, continueWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds, onFinish)
                KeptKind.WATCHLIST -> KeptWall(KeptKind.WATCHLIST, watchlistWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds)
                KeptKind.COLLECTIONS -> {
                    val movies = remember(shelves) { everyFilm(shelves) }
                    CollectionsScreen(
                        franchises = remember(movies) { franchisesIn(movies) },
                        lists = state.watch.collections,
                        setsById = remember(shelves) { allSetsById(shelves) },
                        onOpenFranchise = onOpenFranchise,
                        onOpenList = onOpenList,
                        onCreateList = onCreateList,
                        onOpenTitle = onOpenTitle,
                    )
                }
            }

            is CatalogTab.Dept -> shelves.firstOrNull { it.department == chosenTab.department }?.let { shelf ->
                DepartmentTab(
                    shelf = shelf,
                    state = state,
                    columns = columns,
                    deptScroll = deptScroll,
                    onOpenTitle = onOpenTitle,
                    onOpenCollection = onOpenCollection,
                    onOpenGenre = onOpenGenre,
                    onOpenGenresIndex = onOpenGenresIndex,
                    onOpenLatest = onOpenLatest,
                    onOpenMoviesPage = onOpenMoviesPage,
                    onPlay = { id -> onPlayRun(id, emptyList()) },
                    titleInfo = titleInfo,
                )
            }
        }
    }
}
