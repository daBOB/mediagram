package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.ChromeCounts
import catalog.HOME_POSTER_ROW_LIMIT
import catalog.KeptKind
import catalog.allSetsById
import catalog.catalogTabOf
import catalog.chromeCountsOf
import catalog.hasContent
import catalog.libraryTallyLines
import catalog.magazineHomeOf
import catalog.mastheadTabsOf
import catalog.updateDisabledReason
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.common.MenuActions
import ui.common.RailItem
import ui.common.catalog.rememberDepartmentScrollStates
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.TvDepartmentPill
import ui.tv.chrome.TvLibraryChrome
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue on a television: [TvLibraryChrome] — the rail and the
 * departments bar — around whichever entry is selected: Home, a shelf's
 * wall, or one of the two kept walls the rail chooses directly (My List,
 * Continue). The television twin of the phone's `CatalogScreen`
 * and, through the same web-parity chrome, the tablet's own `LibraryHome`.
 *
 * The chrome stays even while there are no shelves to show — loading, an
 * empty library, a kids profile with nothing rated for it yet, a failed
 * read — carrying only the viewer's name and the way out through ⋮. A kids
 * profile left on a message with nothing else to press would be a dead end
 * otherwise.
 *
 * [restoreKey] names what was last opened from here, handed to whichever
 * wall is showing so Back lands on it — the tab itself is kept by whoever
 * keeps this screen's saved state while it is off screen. [onTabChanged]
 * tells the caller a different tab was chosen, so it can drop that key: it
 * names a plate on the tab that was left, and a film opened from Home would
 * otherwise pull the remote to its plate on Movies.
 *
 * [onOpenSearch]/[onOpenMenu] are the bar's Search and its own ⋮; coming
 * back from either, or from Settings/System/Latest/Genres/Stats now that the
 * rail reaches all five directly, is [rememberTvCatalogRestore]'s own
 * sentinel-key handling. [menu] is what the rail's own Settings and System
 * rows call directly — the same [MenuActions] the trimmed ⋮ page still
 * calls for its own three rows. [onFinish] is Continue's "Mark finished".
 * [onPlay] plays a set straight away — the cover story's own "Watch now" —
 * rather than opening its title page the way [onOpenTitle] does everywhere
 * else this screen leads. [fetching] is the artwork-and-descriptions run
 * the phone reports beside a channel refresh.
 */
@Composable
fun TvCatalogScreen(
    state: CatalogUiState,
    profile: TvChosenProfile,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    menu: MenuActions = NoopMenuActions,
    fetching: Boolean = false,
    restoreKey: String? = null,
    onTabChanged: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onOpenLatest: () -> Unit = {},
    onOpenGenresIndex: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onEntryRestored: () -> Unit = {},
    onFinish: (setId: String) -> Unit = {},
    onOpenGenre: (name: String) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenMoviesPage: () -> Unit = {},
    onPlay: (setId: String) -> Unit = onOpenTitle,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit = { _, _ -> },
) {
    val ready = (state as? CatalogUiState.Ready)?.takeIf { it.shelves.hasContent() }
    val shelves = ready?.shelves.orEmpty()
    // What the bar draws — Home, the departments, Collections — the same
    // function the phone's chrome reads. Continue and My List are the
    // rail's own two rows, chosen directly rather than through a pill.
    val mastheadTabs = remember(shelves) { mastheadTabsOf(shelves) }
    // Every set on any shelf, by id — a Continue/Popular row on a
    // department front page resolves a progress row to its set before
    // narrowing to one kind, and a progress row can name a set of any kind.
    val byId = remember(shelves) { allSetsById(shelves) }
    // Saved by key, not by position: a refresh can return a library with
    // more or fewer departments than the one on screen when it started,
    // which moves every later tab, and a saved position would then reopen
    // on whichever tab now sits there. A department the refresh dropped
    // opens Home.
    var chosenKey by rememberSaveable { mutableStateOf(CatalogTab.Home.key) }
    val selected = catalogTabOf(chosenKey, shelves)
    val choose = { tab: CatalogTab ->
        if (tab != selected) {
            chosenKey = tab.key
            onTabChanged()
        }
    }

    val nav =
        rememberTvCatalogRestore(
            mastheadTabs = mastheadTabs,
            selected = selected,
            ready = ready != null,
            restoreKey = restoreKey,
            choose = choose,
            onEntryRestored = onEntryRestored,
        )

    val counts = remember(shelves, ready?.watch) { ready?.let { chromeCountsOf(shelves, it.watch) } ?: ChromeCounts.Empty }
    val tally = remember(shelves) { libraryTallyLines(shelves) }
    val pills =
        remember(mastheadTabs, counts) {
            mastheadTabs.map { tab -> TvDepartmentPill(tab.label, counts.departmentCount(tab)) }
        }
    val onRailSelect: (RailItem) -> Unit = { item ->
        when (item) {
            RailItem.MY_LIST -> choose(CatalogTab.Kept(KeptKind.WATCHLIST))
            RailItem.CONTINUE_WATCHING -> choose(CatalogTab.Kept(KeptKind.CONTINUE))
            RailItem.LATEST -> onOpenLatest()
            RailItem.GENRES -> onOpenGenresIndex()
            RailItem.STATS -> onOpenStats()
            RailItem.SETTINGS -> menu.onSettings()
            RailItem.SYSTEM -> menu.onSystem()
        }
    }

    // Computed once here, not inside `TvCatalogBody`'s own Home branch: the
    // departments bar below reads this same magazine's own cover to decide
    // whether it has anything to bleed under, so both need the one instance
    // rather than two calls that could disagree on the rare tie that falls
    // right on a day boundary.
    val homeMagazine =
        remember(shelves, ready?.watch, ready?.heldIds, selected) {
            ready?.takeIf { selected == CatalogTab.Home }?.let {
                magazineHomeOf(
                    shelves,
                    it.watch,
                    editorsChoice = it.watch.editorsChoice,
                    now = System.currentTimeMillis(),
                    heldIds = it.heldIds,
                    recentLimit = HOME_POSTER_ROW_LIMIT,
                )
            }
        }
    val homeListState =
        rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = HomeCacheWindow, behind = HomeCacheWindow) })
    val deptScroll = rememberDepartmentScrollStates()
    val hasCover = homeMagazine?.editorial?.cover?.isNotEmpty() == true
    val blend =
        rememberTvCatalogBlend(
            selected = selected,
            shelves = shelves,
            byId = byId,
            watch = ready?.watch,
            homeListState = homeListState,
            homeHasCover = hasCover,
            deptScroll = deptScroll,
        )

    TvLibraryChrome(
        blend = blend,
        pills = if (ready != null) pills else emptyList(),
        selectedPill = nav.selectedPill,
        onSelectPill = nav.onSelectPill,
        railActive = nav.railActive,
        counts = counts,
        tally = tally,
        onRailSelect = onRailSelect,
        onSearch = onOpenSearch,
        profile = profile,
        onMenu = onOpenMenu,
        focus = nav.chromeFocus,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val gutter = LocalTvPagePadding.current
            // Words where the phone draws a bar: the same sentences its
            // Update item gives as the reason it is waiting, so what is
            // happening reads the same wherever a viewer meets it.
            if (ready != null) {
                updateDisabledReason(ready, fetching)?.let {
                    TvQuietLine(it, Modifier.padding(start = gutter.start, end = gutter.end))
                }
            }
            // Above the shelf, not instead of it: the library below is the
            // one that was on this device before the refresh was tried,
            // and it is still every bit of it.
            ready?.notice?.let { notice ->
                Text(
                    text = notice,
                    style = TvTypeScale.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(start = gutter.start, end = gutter.end, top = Spacing.small, bottom = Spacing.small),
                )
            }
            CompositionLocalProvider(LocalTakesArrivalFocus provides nav.takesArrivalFocus) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    // One composition per tab, not one reused across them:
                    // every shelf draws through the same wall, which would
                    // otherwise carry the last shelf's scroll over and
                    // never take the remote from the tab, its first plate
                    // sitting at the same index as before.
                    key(selected) {
                        TvCatalogBody(
                            state = state,
                            ready = ready,
                            shelves = shelves,
                            byId = byId,
                            tab = selected,
                            wallKey = nav.wallKey,
                            railActive = nav.railActive,
                            railRowFocus = nav.chromeFocus.railRowFocus,
                            selectedPillFocus = nav.chromeFocus.selectedPillFocus,
                            homeListState = homeListState,
                            homeMagazine = homeMagazine,
                            deptScroll = deptScroll,
                            onOpenTitle = onOpenTitle,
                            onPlay = onPlay,
                            onOpenCollection = onOpenCollection,
                            onOpenList = onOpenList,
                            onCreateList = onCreateList,
                            onOpenGenre = onOpenGenre,
                            onOpenFranchise = onOpenFranchise,
                            onOpenMoviesPage = onOpenMoviesPage,
                            onFinish = onFinish,
                            onToggleWatchlist = onToggleWatchlist,
                            choose = choose,
                        )
                    }
                }
            }
        }
    }
}

/** [TvCatalogScreen]'s own default: every previewing caller of this composable that has no rail rows to wire yet passes nothing, and the rail's Settings/System rows harmlessly do nothing rather than crashing on a missing [MenuActions]. */
private val NoopMenuActions =
    MenuActions(onSystem = {}, onSettings = {}, onUpdate = {}, onTmdbKey = {}, onStartOver = {})

/**
 * How far past Home's own viewport its `LazyColumn` keeps a section
 * composed. Six sections at most, none of them a plate wall's own hundreds
 * — this can afford to be generous enough that every section stays
 * composed almost all the time on a 540dp screen, which is the mitigation
 * for `Down` reaching a section the list would otherwise not have decided
 * to compose yet.
 */
private val HomeCacheWindow = 900.dp
