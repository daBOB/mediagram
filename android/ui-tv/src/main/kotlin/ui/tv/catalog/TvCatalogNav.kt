package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import catalog.MastheadSplit
import ui.tv.TvContinueEntryKey
import ui.tv.TvWatchlistEntryKey

/**
 * Where the masthead's own row and the restore-key sentinels
 * ([TvSearchEntryKey], [TvMenuEntryKey], [TvContinueEntryKey],
 * [TvWatchlistEntryKey]) leave the remote once [TvCatalogScreen] knows what
 * is selected — split out of it so that composable reads as "what shows
 * below the masthead", not also "which of eight index slots that is and
 * where Back from four different sentinels sends the remote".
 */
internal class TvCatalogRestore(
    val mastheadSelected: Int,
    val onMastheadSelect: (Int) -> Unit,
    val selectedTabFocus: FocusRequester,
    val searchFocus: FocusRequester,
    val menuFocus: FocusRequester,
    val wallFocus: FocusRequester,
    val backToMasthead: Boolean,
    val wallKey: String?,
)

@Composable
internal fun rememberTvCatalogRestore(
    masthead: MastheadSplit,
    selected: Int,
    shelfCount: Int,
    collectionsIndex: Int,
    continueIndex: Int,
    watchlistIndex: Int,
    ready: Boolean,
    restoreKey: String?,
    mastheadFocus: FocusRequester,
    choose: (Int) -> Unit,
    onEntryRestored: () -> Unit,
): TvCatalogRestore {
    // The masthead's own tab index space is departments-only: Home, the
    // shelves, then Collections at the end — Continue/Watchlist have no
    // masthead position any more, so a viewer on either sees no tab
    // selected (`-1`, which every entry in the row simply is not).
    val mastheadSelected =
        when {
            selected <= shelfCount -> selected
            selected == collectionsIndex -> masthead.departments.lastIndex
            else -> -1
        }
    val onMastheadSelect = { visiblePosition: Int ->
        choose(if (visiblePosition == masthead.departments.lastIndex) collectionsIndex else visiblePosition)
    }

    val selectedTab = remember { FocusRequester() }
    val search = remember { FocusRequester() }
    val menu = remember { FocusRequester() }
    val wall = remember { FocusRequester() }
    val backFromSearch = restoreKey == TvSearchEntryKey
    val backFromMenu = restoreKey == TvMenuEntryKey
    val backFromContinue = restoreKey == TvContinueEntryKey
    val backFromWatchlist = restoreKey == TvWatchlistEntryKey
    val backToMasthead = backFromSearch || backFromMenu
    val wallKey = restoreKey.takeUnless { backToMasthead || backFromContinue || backFromWatchlist }

    // With no wall below to take focus, the masthead is the one thing on
    // screen the remote can rest on.
    LaunchedEffect(!ready) { if (!ready) mastheadFocus.requestFocus() }
    LaunchedEffect(backFromSearch, ready) {
        if (backFromSearch && ready) {
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
    LaunchedEffect(backFromContinue, ready) {
        if (backFromContinue && ready) {
            choose(continueIndex)
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromWatchlist, ready) {
        if (backFromWatchlist && ready) {
            choose(watchlistIndex)
            onEntryRestored()
        }
    }

    return TvCatalogRestore(mastheadSelected, onMastheadSelect, selectedTab, search, menu, wall, backToMasthead, wallKey)
}
