package catalog

/**
 * [CatalogViewModel]'s own use of [playback.HeldSetsQuery] — split out to
 * keep that file under the project's line guideline.
 */

/** [CatalogViewModel.heldSets] asked over the whole catalog, off-main inside `HeldSetsQuery.heldIds` itself. */
internal suspend fun CatalogViewModel.heldIdsOf(shelves: List<Shelf>): Set<String> =
    heldSets.heldIds(indexById(shelves).values.map { it.setId to it.totalBytes })

/** [setId] just finished taking into the cache; folded into the shelves already up rather than a full rescan. */
internal fun CatalogViewModel.heldEventApplied(setId: String): CatalogUiState.Ready? {
    val kept = lastReady ?: return null
    if (setId in kept.heldIds) return null
    return kept.copy(heldIds = kept.heldIds + setId).also { lastReady = it }
}
