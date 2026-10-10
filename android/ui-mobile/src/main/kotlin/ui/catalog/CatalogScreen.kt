package ui.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.magazineHomeOf
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo

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
