package ui

import catalog.CatalogUiState
import catalog.Division
import catalog.Entry
import catalog.collection
import catalog.mediaSet
import model.ListOfSets
import model.MediaSet
import model.WatchSnapshot

/**
 * Every key [LibraryPositions] holds, looked up again against the catalog
 * as it stands. `null` while the shelves are still loading, or once
 * whatever a key named is no longer there — both the same "not yet, or not
 * any more" a stale saved position always resolves to.
 */
internal data class ResolvedPositions(
    val collection: Entry.Collection?,
    val title: MediaSet?,
    val season: Division?,
    val watch: WatchSnapshot,
    val list: ListOfSets?,
)

/**
 * Resolved once per recomposition rather than at each of [LibraryBranches]'s
 * branches: every one of them wants the same lookups, and none has another
 * way to reach them.
 */
internal fun LibraryPositions.resolve(catalogState: CatalogUiState): ResolvedPositions {
    val resolvedCollection = collection?.let(catalogState::collection)
    val resolvedTitle = titleId?.let(catalogState::mediaSet)
    // A season is one of its show's own divisions, so it only exists once
    // the show it belongs to does; a stale key from a different show simply
    // fails to find a match rather than opening the wrong season.
    val resolvedSeason = season?.let { name -> resolvedCollection?.divisions?.find { it.title == name } }
    val watch = (catalogState as? CatalogUiState.Ready)?.watch ?: WatchSnapshot.Empty
    // Resolved the same way a collection is: a saved id, looked up again
    // against whatever the snapshot currently holds, so a list renamed or
    // filled on another device resolves to the current row rather than a
    // stale copy.
    val resolvedList = listId?.let { id -> watch.collections.find { it.id == id } }
    return ResolvedPositions(resolvedCollection, resolvedTitle, resolvedSeason, watch, resolvedList)
}
