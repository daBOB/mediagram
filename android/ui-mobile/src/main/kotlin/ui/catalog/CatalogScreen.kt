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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CatalogUiState
import catalog.KeptKind
import catalog.Shelf
import catalog.catalogTabsOf
import catalog.continueWall
import catalog.homeRowsOf
import catalog.magazineHomeOf
import catalog.watchlistWall
import designsystem.Spacing
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * The shelves, and one line above them while the library is being worked
 * on. [fetching] is the other run that changes what is on these shelves —
 * it fills in the artwork on them, and the descriptions behind them — and
 * it is reported here rather than beside itself, because a viewer watching
 * something happen should not have to learn a second vocabulary for it
 * depending on which menu item started it.
 */
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    fetching: Boolean,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
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
        is CatalogUiState.Ready -> Shelves(state, fetching, onOpenTitle, onOpenCollection, onOpenList, onCreateList, onPlayRun, onFinish, titleInfo)
    }
}

/**
 * One shelf on screen, chosen from the masthead above it.
 *
 * The masthead carries seven entries, the web's own order: Home, the three
 * catalog shelves, then the three kept from watch state — see [ShelfTabs].
 * The shelf a viewer was last on is kept across a rotation and a process
 * death, because coming back to the top of the film shelf after glancing
 * at something else is the kind of small forgetting that makes an app feel
 * like it is not paying attention.
 */
@Composable
private fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    /** Continue's "Mark finished". */
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
    // Ordering, index-to-tab mapping and the tab labels themselves are
    // catalogTabsOf's — see CatalogTabs for why Home comes first.
    val tabs = remember(shelves) { catalogTabsOf(shelves) }
    val titles = tabs.titles
    val firstKept = tabs.firstKept
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    // A refresh can return a library with fewer shelves than the one that
    // was on screen when it started.
    val selected = chosen.coerceIn(0, titles.lastIndex)
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned above the wall rather than scrolling inside it: it reports
        // on the whole library, not on a row of it, and a viewer who has
        // scrolled down is exactly the one who would otherwise watch the
        // shelves change under their thumb with nothing having said why.
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ShelfTabs(titles = titles, selected = selected, firstKeptIndex = firstKept, onSelect = { chosen = it })
        // Above the shelf, not instead of it: the library below is the one
        // that was on this device before the refresh was tried, and it is
        // still every bit of it.
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
                magazine =
                    remember(shelves, state.watch, state.heldIds) {
                        magazineHomeOf(
                            shelves,
                            state.watch,
                            editorsChoice = state.watch.editorsChoice,
                            now = System.currentTimeMillis(),
                            heldIds = state.heldIds,
                        )
                    },
                // The magazine header already carries Continue, Next up and
                // the Movies shelf's own row under different headings —
                // dropping them here is what keeps the page from showing
                // each once as itself and once again as a plain row.
                rows =
                    remember(shelves, state.watch, state.heldIds) {
                        homeRowsOf(shelves, state.watch, state.heldIds)
                            .filterNot { it.title in setOf("Continue", "Next up", "Latest films") }
                    },
                watch = state.watch,
                columns = columns,
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onSeeAll = { shelf -> chosen = titles.indexOf(shelf).coerceAtLeast(0) },
            )

            selected < firstKept -> ShelfWall(
                shelves[selected - 1],
                state.watch,
                state.heldIds,
                columns,
                shelfView,
                onOpenTitle,
                onOpenCollection,
                films = FilmShelfActions(onPlay = { onPlayRun(it.setId, emptyList()) }, titleInfo = titleInfo),
            )

            else -> KeptTabContent(
                kind = KeptKind.entries[selected - firstKept],
                shelves = shelves,
                watch = state.watch,
                heldIds = state.heldIds,
                columns = columns,
                onOpenTitle = onOpenTitle,
                onOpenCollection = onOpenCollection,
                onOpenList = onOpenList,
                onCreateList = onCreateList,
                onPlayRun = onPlayRun,
                onFinish = onFinish,
            )
        }
    }
}

/** Which of the masthead's three kept tabs is selected, dispatched to what draws it. */
@Composable
private fun KeptTabContent(
    kind: KeptKind,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    columns: Int,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    /** Continue's "Mark finished". */
    onFinish: (setId: String) -> Unit,
) {
    when (kind) {
        KeptKind.CONTINUE -> KeptWall(kind, continueWall(shelves, watch), watch, columns, onOpenTitle, heldIds, onFinish)
        KeptKind.WATCHLIST -> KeptWall(kind, watchlistWall(shelves, watch), watch, columns, onOpenTitle, heldIds)
        KeptKind.COLLECTIONS -> ListsScreen(lists = watch.collections, onOpen = onOpenList, onCreate = onCreateList)
    }
}
