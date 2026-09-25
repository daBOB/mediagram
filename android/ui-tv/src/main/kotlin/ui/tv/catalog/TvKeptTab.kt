package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import catalog.KeptKind
import catalog.Shelf
import catalog.continueWall
import catalog.kidsShelf
import catalog.watchlistWall
import model.WatchSnapshot

/**
 * Which of the masthead's four kept entries is selected, dispatched to what
 * draws it — the phone's `KeptTabContent`, over the same four functions.
 */
@Composable
internal fun TvKeptTab(
    kind: KeptKind,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    restoreKey: String?,
) {
    when (kind) {
        KeptKind.CONTINUE -> TvKeptWall(kind, remember(shelves, watch) { continueWall(shelves, watch) }, watch, onOpenTitle, restoreKey)
        KeptKind.WATCHLIST -> TvKeptWall(kind, remember(shelves, watch) { watchlistWall(shelves, watch) }, watch, onOpenTitle, restoreKey)
        KeptKind.KIDS -> TvKidsWall(remember(shelves, watch) { kidsShelf(shelves, watch) }, watch, onOpenTitle, onOpenCollection, restoreKey)
        KeptKind.COLLECTIONS -> TvLists(lists = watch.collections, onOpen = onOpenList, onCreate = onCreateList, restoreKey = restoreKey)
    }
}
