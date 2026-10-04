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
 * Continue or My List, dispatched to the wall that draws it — the phone's
 * `KeptTabContent`, over the same two functions. [tabFocus] is the rail row
 * that chose this, where an empty wall sends the remote, having no plate of
 * its own to hold it. [onFinish] is Continue's "Mark finished", and
 * Continue's alone, as on the phone and the web.
 *
 * The third kept kind, Collections, never arrives here: it is the
 * departments bar's own last entry, and [TvCatalogBody] draws it as
 * [TvCollectionsPage] before it could ever reach a kept tab.
 */
@Composable
internal fun TvKeptTab(
    kind: KeptKind,
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
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
        KeptKind.COLLECTIONS -> error("Collections is drawn by TvCollectionsPage, never as a kept tab")
    }
}
