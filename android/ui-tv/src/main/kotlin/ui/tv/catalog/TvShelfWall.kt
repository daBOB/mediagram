package ui.tv.catalog

import androidx.compose.runtime.Composable
import catalog.Shelf
import catalog.keyOf
import model.WatchSnapshot

/**
 * Everything one catalog shelf holds, on one wall — the television twin of
 * the phone's `ShelfWall`. The entries arrive already grouped by
 * `feature:catalog`, so a show is one plate here as it is on the phone and
 * the web, and this only draws them.
 *
 * A film opens its title page and a show or a course opens what is inside
 * it, through [openEntry]. Coming back lands on the plate [restoreKey]
 * names, the one that was opened. A film this device holds carries the
 * offline badge, as on the phone's shelf walls.
 */
@Composable
internal fun TvShelfWall(
    shelf: Shelf,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)

    TvWall(
        items = shelf.entries,
        key = ::keyOf,
        restoreKey = restoreKey,
        onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
        plate = { entry, modifier, onOpen ->
            TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds)
        },
    )
}
