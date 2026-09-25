package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import catalog.KeptKind
import catalog.Shelf
import catalog.continueWall
import catalog.watchlistWall
import model.WatchSnapshot

/**
 * Which of the masthead's three kept entries is selected, dispatched to what
 * draws it — the phone's `KeptTabContent`, over the same three functions.
 * [tabFocus] is the masthead tab that chose this, where an empty wall
 * sends the remote, having no plate of its own to hold it.
 */
@Composable
internal fun TvKeptTab(
    kind: KeptKind,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    tabFocus: FocusRequester,
    restoreKey: String?,
) {
    when (kind) {
        KeptKind.CONTINUE ->
            TvKeptWall(kind, remember(shelves, watch) { continueWall(shelves, watch) }, watch, onOpenTitle, tabFocus, restoreKey)
        KeptKind.WATCHLIST ->
            TvKeptWall(kind, remember(shelves, watch) { watchlistWall(shelves, watch) }, watch, onOpenTitle, tabFocus, restoreKey)
        KeptKind.COLLECTIONS -> TvLists(lists = watch.collections, onOpen = onOpenList, onCreate = onCreateList, restoreKey = restoreKey)
    }
}
