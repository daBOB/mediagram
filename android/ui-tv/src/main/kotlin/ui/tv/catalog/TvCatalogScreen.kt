package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CatalogUiState
import catalog.ChromeCounts
import catalog.allSetsById
import catalog.catalogTabsOf
import catalog.chromeCountsOf
import catalog.hasContent
import catalog.libraryTallyLines
import catalog.mastheadSplitOf
import catalog.updateDisabledReason
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.MenuActions
import ui.RailItem
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.TvDepartmentPill
import ui.tv.chrome.TvLibraryChrome
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue on a television: [TvLibraryChrome] — the rail and the
 * departments bar — around whichever entry is selected: Home, a shelf's
 * wall, or one of the two kept walls the rail chooses directly (My List,
 * Continue watching). The television twin of the phone's `CatalogScreen`
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
 * back from either, or from Settings/System/Latest/Genres now that the rail
 * reaches all four directly, is [rememberTvCatalogRestore]'s own
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
    onEntryRestored: () -> Unit = {},
    onFinish: (setId: String) -> Unit = {},
    onOpenGenre: (name: String) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenMoviesPage: () -> Unit = {},
    onPlay: (setId: String) -> Unit = onOpenTitle,
) {
    val ready = (state as? CatalogUiState.Ready)?.takeIf { it.shelves.hasContent() }
    val shelves = ready?.shelves.orEmpty()
    // Ordering, index-to-tab mapping and the labels themselves are
    // catalogTabsOf's, the same function the phone's chrome reads — the
    // full 8-wide index space (Home, shelves, Continue, Watchlist,
    // Collections) that `chosen` still lives in, unchanged by the
    // department split below: only what the bar *draws* narrows, not what
    // a tab index means.
    val tabs = remember(shelves) { catalogTabsOf(shelves) }
    // What the bar itself draws — departments only: Continue and Watchlist
    // are the rail's own two kept rows now, chosen directly rather than
    // through a sentinel restore key; Collections moves from the third
    // kept tab into the department row's own last entry.
    val masthead = remember(shelves) { mastheadSplitOf(shelves) }
    // Every set on any shelf, by id — a Continue/Popular row on a
    // department front page resolves a progress row to its set before
    // narrowing to one kind, and a progress row can name a set of any kind.
    val byId = remember(shelves) { allSetsById(shelves) }
    val continueIndex = tabs.firstKept
    val watchlistIndex = tabs.firstKept + 1
    val collectionsIndex = tabs.firstKept + 2
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    // A refresh can return a library with fewer shelves than the one that
    // was on screen when it started.
    val selected = chosen.coerceIn(0, tabs.titles.lastIndex)
    val choose = { index: Int ->
        if (index != selected) {
            chosen = index
            onTabChanged()
        }
    }

    val nav =
        rememberTvCatalogRestore(
            masthead = masthead,
            selected = selected,
            shelfCount = shelves.size,
            collectionsIndex = collectionsIndex,
            continueIndex = continueIndex,
            watchlistIndex = watchlistIndex,
            ready = ready != null,
            restoreKey = restoreKey,
            choose = choose,
            onEntryRestored = onEntryRestored,
        )

    val counts = remember(shelves, ready?.watch) { ready?.let { chromeCountsOf(shelves, it.watch) } ?: ChromeCounts.Empty }
    val tally = remember(shelves) { libraryTallyLines(shelves) }
    val pills =
        remember(masthead, counts) {
            masthead.departments.map { title -> TvDepartmentPill(title, counts.departmentCount(title)) }
        }
    val onRailSelect: (RailItem) -> Unit = { item ->
        when (item) {
            RailItem.MY_LIST -> choose(watchlistIndex)
            RailItem.CONTINUE_WATCHING -> choose(continueIndex)
            RailItem.LATEST -> onOpenLatest()
            RailItem.GENRES -> onOpenGenresIndex()
            RailItem.SETTINGS -> menu.onSettings()
            RailItem.SYSTEM -> menu.onSystem()
        }
    }

    TvLibraryChrome(
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
                            tabs = tabs,
                            selected = selected,
                            collectionsIndex = collectionsIndex,
                            wallKey = nav.wallKey,
                            railActive = nav.railActive,
                            railRowFocus = nav.chromeFocus.railRowFocus,
                            onOpenTitle = onOpenTitle,
                            onPlay = onPlay,
                            onOpenCollection = onOpenCollection,
                            onOpenList = onOpenList,
                            onCreateList = onCreateList,
                            onOpenGenre = onOpenGenre,
                            onOpenFranchise = onOpenFranchise,
                            onOpenMoviesPage = onOpenMoviesPage,
                            onFinish = onFinish,
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
