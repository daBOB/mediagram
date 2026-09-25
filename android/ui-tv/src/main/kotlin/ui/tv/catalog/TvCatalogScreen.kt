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
import catalog.KeptKind
import catalog.catalogTabsOf
import catalog.homeRowsOf
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
 * [onOpenSearch] is the masthead's Search. Coming back from it —
 * [restoreKey] is then [TvSearchEntryKey] — the remote goes back to Search
 * rather than down to the wall, which leaves it alone for once, and
 * [onSearchRestored] then lets the caller forget that key: it has done its
 * work, and the walls below must take the remote again whenever they
 * otherwise would. They are never handed the key itself, so forgetting it
 * is not a new key to them and pulls nothing down from Search. Down from
 * Search goes into the wall below by the wall's own first stop, not to
 * whichever plate happens to sit under the far end of the masthead.
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
    onSearchRestored: () -> Unit = {},
    onFinish: (setId: String) -> Unit = {},
) {
    val ready = (state as? CatalogUiState.Ready)?.takeIf { it.shelves.isNotEmpty() }
    val shelves = ready?.shelves.orEmpty()
    // Ordering, index-to-tab mapping and the labels themselves are
    // catalogTabsOf's, the same function the phone's masthead reads.
    val tabs = remember(shelves) { catalogTabsOf(shelves) }
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

    val selectedTab = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val backFromSearch = restoreKey == TvSearchEntryKey
    val wallKey = restoreKey.takeUnless { backFromSearch }
    val wall = remember { FocusRequester() }

    // With no wall below to take focus, the masthead is the one thing on
    // screen the remote can rest on.
    LaunchedEffect(ready == null) {
        if (ready == null) mastheadFocus.requestFocus()
    }
    LaunchedEffect(backFromSearch, ready != null) {
        if (backFromSearch && ready != null) {
            search.requestFocus()
            onSearchRestored()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TvMasthead(
            titles = if (ready != null) tabs.titles else emptyList(),
            selected = selected,
            firstKeptIndex = tabs.firstKept,
            profile = profile,
            onSelect = choose,
            focusRequester = mastheadFocus,
            selectedFocus = selectedTab,
            onSearch = onOpenSearch,
            searchFocus = search,
            searchDown = wall,
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
        CompositionLocalProvider(LocalTakesArrivalFocus provides !backFromSearch) {
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
                                rows = remember(shelves, ready.watch, ready.heldIds) { homeRowsOf(shelves, ready.watch, ready.heldIds) },
                                watch = ready.watch,
                                onOpenTitle = onOpenTitle,
                                onOpenCollection = onOpenCollection,
                                onSeeAll = { shelf -> choose(tabs.titles.indexOf(shelf).coerceAtLeast(0)) },
                                restoreKey = wallKey,
                            )
                        }
                        selected < tabs.firstKept -> {
                            TvShelfWall(shelves[selected - 1], ready.watch, onOpenTitle, onOpenCollection, wallKey, ready.heldIds)
                        }
                        else -> {
                            TvKeptTab(
                                kind = KeptKind.entries[selected - tabs.firstKept],
                                shelves = shelves,
                                watch = ready.watch,
                                onOpenTitle = onOpenTitle,
                                onOpenList = onOpenList,
                                onCreateList = onCreateList,
                                tabFocus = selectedTab,
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
