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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CatalogUiState
import catalog.allSetsById
import catalog.catalogTabsOf
import catalog.mastheadSplitOf
import catalog.updateDisabledReason
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
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
 * [onOpenSearch]/[onOpenMenu] are the masthead's Search and Menu; coming back
 * from either, or from the overflow menu's Continue/Watchlist rows, is
 * [rememberTvCatalogRestore]'s own sentinel-key handling. [onFinish] is
 * Continue's "Mark finished". [onPlay] plays a set straight away — the cover
 * story's own "Watch now" — rather than opening its title page the way
 * [onOpenTitle] does everywhere else this screen leads. [fetching] is the
 * artwork-and-descriptions run the phone reports beside a channel refresh.
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
    onPlay: (setId: String) -> Unit = onOpenTitle,
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
    // What the masthead itself draws — departments only: Continue and
    // Watchlist move to the overflow menu, reached instead by the two
    // sentinel restore keys `TvCatalogNav` resolves; Collections moves from
    // the third kept tab into the department row's own last entry.
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
            mastheadFocus = mastheadFocus,
            choose = choose,
            onEntryRestored = onEntryRestored,
        )

    Column(modifier = Modifier.fillMaxSize()) {
        TvMasthead(
            titles = if (ready != null) masthead.departments else emptyList(),
            selected = nav.mastheadSelected,
            firstKeptIndex = masthead.departments.lastIndex,
            profile = profile,
            onSelect = nav.onMastheadSelect,
            focusRequester = mastheadFocus,
            selectedFocus = nav.selectedTabFocus,
            onSearch = onOpenSearch,
            searchFocus = nav.searchFocus,
            searchDown = nav.wallFocus,
            onMenu = onOpenMenu,
            menuFocus = nav.menuFocus,
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
        CompositionLocalProvider(LocalTakesArrivalFocus provides !nav.backToMasthead) {
            Box(modifier = Modifier.fillMaxSize().focusRequester(nav.wallFocus)) {
                // One composition per tab, not one reused across them: every
                // shelf draws through the same wall, which would otherwise carry
                // the last shelf's scroll over and never take the remote from the
                // tab, its first plate sitting at the same index as before.
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
                        mastheadSelected = nav.mastheadSelected,
                        selectedTabFocus = nav.selectedTabFocus,
                        mastheadFocus = mastheadFocus,
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
