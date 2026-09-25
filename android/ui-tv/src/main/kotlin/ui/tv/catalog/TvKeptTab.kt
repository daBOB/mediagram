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
 * sends the remote, having no plate of its own to hold it. [onFinish] is
 * Continue's "Mark finished", and Continue's alone, as on the phone and
 * the web.
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
    heldIds: Set<String> = emptySet(),
    onFinish: (setId: String) -> Unit = {},
) {
    when (kind) {
        KeptKind.CONTINUE -> {
            val sets = remember(shelves, watch) { continueWall(shelves, watch) }
            TvKeptWall(kind, sets, watch, onOpenTitle, tabFocus, restoreKey, heldIds, onFinish)
        }
        KeptKind.WATCHLIST -> {
            val sets = remember(shelves, watch) { watchlistWall(shelves, watch) }
            TvKeptWall(kind, sets, watch, onOpenTitle, tabFocus, restoreKey, heldIds)
        }
        KeptKind.COLLECTIONS -> TvLists(lists = watch.collections, onOpen = onOpenList, onCreate = onCreateList, restoreKey = restoreKey)
    }
}
