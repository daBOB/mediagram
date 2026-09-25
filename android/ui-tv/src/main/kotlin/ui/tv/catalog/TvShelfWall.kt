package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * it, through [openEntry].
 */
@Composable
internal fun TvShelfWall(
    shelf: Shelf,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
) {
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }

    TvWall(
        items = shelf.entries,
        key = ::keyOf,
        restoreKey = null,
        onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
        plate = { entry, modifier, onOpen ->
            TvEntryPlate(entry = entry, positions = positions, watchedIds = watchedIds, onOpen = onOpen, modifier = modifier)
        },
    )
}
