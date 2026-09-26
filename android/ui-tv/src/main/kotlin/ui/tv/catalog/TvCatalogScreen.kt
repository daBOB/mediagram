package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CatalogUiState
import catalog.Entry
import catalog.KeptKind
import catalog.catalogTabsOf
import catalog.franchisesIn
import catalog.homeRowsOf
import catalog.magazineHomeOf
import catalog.mastheadSplitOf
import catalog.updateDisabledReason
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvContinueEntryKey
import ui.tv.TvWatchlistEntryKey
import ui.tv.profile.TvChosenProfile

/**
 * The catalogue on a television: [TvMasthead] across the top and, below
 * it, whichever entry is selected — Home, a shelf's wall, or one of the
 * three kept entries. The television twin of the phone's `CatalogScreen`,
 * with the same callbacks out, so whatever owns the positions around it
 * drives both surfaces the same way.
 *
 * The masthead stays even while there are no shelves to show — loading,
 * an empty library, a kids profile with nothing rated for it yet, a failed
 * read — carrying only the viewer's name then. On the phone the bar's
 * profile button is that way out; here the masthead is the bar, and a kids
 * profile left on a message with nothing to press would be a dead end.
 *
 * [mastheadFocus] is the handle a caller uses to send the remote back up to
 * the masthead, which is where Back goes first from the catalogue's root;
 * [onMastheadFocusChanged] tells that caller when the remote is already
 * there. [restoreKey] names what was last opened from here, handed to
 * whichever wall is showing so Back lands on it — the tab itself is kept
 * by whoever keeps this screen's saved state while it is off screen.
 * [onTabChanged] tells the caller a different tab was chosen, so it can
 * drop that key: it names a plate on the tab that was left, and a film
 * opened from Home would otherwise pull the remote to its plate on Movies.
 *
 * [onOpenSearch] is the masthead's Search. Coming back from it —
 * [restoreKey] is then [TvSearchEntryKey] — the remote goes back to Search
 * rather than down to the wall, which leaves it alone for once, and
 * [onEntryRestored] then lets the caller forget that key: it has done its
 * work, and the walls below must take the remote again whenever they
 * otherwise would. They are never handed the key itself, so forgetting it
 * is not a new key to them and pulls nothing down from Search. Down from
 * Search goes into the wall below by the wall's own first stop, not to
 * whichever plate happens to sit under the far end of the masthead.
 *
 * [onOpenMenu] is the masthead's Menu, and coming back from it —
 * [restoreKey] is then [TvMenuEntryKey] — works the same way, the remote
 * going back to Menu. That holds with no shelves too, where Menu is the
 * way to Start over.
 *
 * [onFinish] is Continue's "Mark finished".
 *
 * [fetching] is the artwork-and-descriptions run the phone reports on the
 * same line as a channel refresh: both change what is on these shelves, so
 * a viewer watching one happen reads the same place for either.
 */
@Composable
fun TvCatalogScreen(
    state: CatalogUiState,
    profile: TvChosenProfile,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    mastheadFocus: FocusRequester = remember { FocusRequester() },
    fetching: Boolean = false,
    restoreKey: String? = null,
    onMastheadFocusChanged: (Boolean) -> Unit = {},
    onTabChanged: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onEntryRestored: () -> Unit = {},
    onFinish: (setId: String) -> Unit = {},
    onOpenGenre: (name: String) -> Unit = {},
    onOpenFranchise: (id: Long) -> Unit = {},
    onOpenMoviesPage: () -> Unit = {},
) {
    val ready = (state as? CatalogUiState.Ready)?.takeIf { it.shelves.isNotEmpty() }
    val shelves = ready?.shelves.orEmpty()
    // Ordering, index-to-tab mapping and the labels themselves are
    // catalogTabsOf's, the same function the phone's masthead reads — the
    // full 8-wide index space (Home, shelves, Continue, Watchlist,
    // Collections) that `chosen` still lives in, unchanged by the masthead
    // split below: only what the masthead *draws* narrows, not what a tab
    // index means.
    val tabs = remember(shelves) { catalogTabsOf(shelves) }
    // What the masthead itself draws — departments only, `mastheadSplitOf`'s
    // own decision (phase 3): Continue and Watchlist move to the overflow
    // menu, reached instead by the two sentinel restore keys below; Collections
    // moves from the third kept tab into the department row's own last entry.
    val masthead = remember(shelves) { mastheadSplitOf(shelves) }
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
    // The masthead's own tab index space is departments-only: Home, the
    // shelves, then Collections at the end — Continue/Watchlist have no
    // masthead position any more, so a viewer on either sees no tab
    // selected (`-1`, which every entry in the row simply is not).
    val mastheadSelected =
        when {
            selected <= shelves.size -> selected
            selected == collectionsIndex -> masthead.departments.lastIndex
            else -> -1
        }
    val onMastheadSelect = { visiblePosition: Int ->
        choose(if (visiblePosition == masthead.departments.lastIndex) collectionsIndex else visiblePosition)
    }

    val selectedTab = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val menu = remember { FocusRequester() }
    val backFromSearch = restoreKey == TvSearchEntryKey
    val backFromMenu = restoreKey == TvMenuEntryKey
    val backFromContinue = restoreKey == TvContinueEntryKey
    val backFromWatchlist = restoreKey == TvWatchlistEntryKey
    val backToMasthead = backFromSearch || backFromMenu
    val wallKey = restoreKey.takeUnless { backToMasthead || backFromContinue || backFromWatchlist }
    val wall = remember { FocusRequester() }

    // With no wall below to take focus, the masthead is the one thing on
    // screen the remote can rest on.
    LaunchedEffect(ready == null) {
        if (ready == null) mastheadFocus.requestFocus()
    }
    LaunchedEffect(backFromSearch, ready != null) {
        if (backFromSearch && ready != null) {
            search.requestFocus()
            onEntryRestored()
        }
    }
    // After the effect above that sends an empty catalogue's remote to the
    // masthead, so Menu is where it rests rather than the viewer's name.
    LaunchedEffect(backFromMenu) {
        if (backFromMenu) {
            menu.requestFocus()
            onEntryRestored()
        }
    }
    // The overflow menu's own "My List"/"Continue watching" rows: no
    // masthead tab to reopen, so this selects the same hidden tab index the
    // old three-kept-tabs masthead used to carry, and leaves the remote for
    // that tab's own wall to take, the same as choosing a real tab does.
    LaunchedEffect(backFromContinue, ready != null) {
        if (backFromContinue && ready != null) {
            choose(continueIndex)
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromWatchlist, ready != null) {
        if (backFromWatchlist && ready != null) {
            choose(watchlistIndex)
            onEntryRestored()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvMasthead(
            titles = if (ready != null) masthead.departments else emptyList(),
            selected = mastheadSelected,
            firstKeptIndex = masthead.departments.lastIndex,
            profile = profile,
            onSelect = onMastheadSelect,
            focusRequester = mastheadFocus,
            selectedFocus = selectedTab,
            onSearch = onOpenSearch,
            searchFocus = search,
            searchDown = wall,
            onMenu = onOpenMenu,
            menuFocus = menu,
            modifier = Modifier.onFocusChanged { onMastheadFocusChanged(it.hasFocus) },
        )
        // Words where the phone draws a bar: the same sentences its Update
        // item gives as the reason it is waiting, so what is happening reads
        // the same wherever a viewer meets it.
        if (ready != null) {
            updateDisabledReason(ready, fetching)?.let { TvQuietLine(it, Modifier.padding(horizontal = Overscan.horizontal)) }
        }
        // Above the shelf, not instead of it: the library below is the one
        // that was on this device before the refresh was tried, and it is
        // still every bit of it.
        ready?.notice?.let { notice ->
            Text(
                text = notice,
                style = TvTypeScale.body,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Overscan.horizontal, vertical = Spacing.small),
            )
        }
        CompositionLocalProvider(LocalTakesArrivalFocus provides !backToMasthead) {
            Box(modifier = Modifier.fillMaxSize().focusRequester(wall)) {
                // One composition per tab, not one reused across them: every
                // shelf draws through the same wall, which would otherwise carry
                // the last shelf's scroll over and never take the remote from the
                // tab, its first plate sitting at the same index as before.
                key(selected) {
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
                                onOpenCollection = onOpenCollection,
                                onSeeAll = { shelf -> choose(tabs.titles.indexOf(shelf).coerceAtLeast(0)) },
                                restoreKey = wallKey,
                                // The magazine header already carries its own
                                // "Recently added" row over the Movies shelf —
                                // dropping "Latest films" above is what keeps
                                // Home from showing the same films twice.
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
                                onOpenTitle = onOpenTitle,
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
                                // Continue and Watchlist have no masthead
                                // tab of their own any more (reached from the
                                // overflow menu instead), so `selectedTab` —
                                // attached only to whichever tab the masthead
                                // itself currently marks selected — is never
                                // claimed while one of them is showing, and
                                // asking it to take focus would find nothing
                                // to land on. `mastheadFocus` is the row's own
                                // requester, always attached, so it is what a
                                // wall that empties under the viewer falls
                                // back to instead.
                                tabFocus = if (mastheadSelected >= 0) selectedTab else mastheadFocus,
                                restoreKey = wallKey,
                                heldIds = ready.heldIds,
                                onFinish = onFinish,
                            )
                        }
                    }
                }
            }
        }
    }
}
