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
data class ResolvedPositions(
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
fun LibraryPositions.resolve(catalogState: CatalogUiState): ResolvedPositions {
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

/**
 * What a frame whose key does not resolve draws — pure, so the rule is
 * tested without a `Composable`. [FrameKind.TITLE], [FrameKind.SEASON],
 * [FrameKind.COLLECTION] and [FrameKind.LIST] all read their own value
 * from a lookup against the catalog, and used to draw nothing at all —
 * no bar, no back handler — when it came back `null`, so back finished
 * the Activity instead of leaving the screen. That happened on a restore
 * landing here before the catalog loaded, or on a stale key left over
 * once whatever it named was deleted elsewhere.
 */
sealed interface FrameResolution<out T> {
    data class Resolved<T>(val value: T) : FrameResolution<T>

    /** Not yet answered by the catalog — "not yet", not "gone". */
    data object Loading : FrameResolution<Nothing>

    /** The catalog answered and the key still names nothing — gone for good. */
    data object Stale : FrameResolution<Nothing>
}

fun <T> resolveFrame(value: T?, catalogReady: Boolean): FrameResolution<T> = when {
    value != null -> FrameResolution.Resolved(value)
    !catalogReady -> FrameResolution.Loading
    else -> FrameResolution.Stale
}
