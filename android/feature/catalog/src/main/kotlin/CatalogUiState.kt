package catalog

/** What the catalog screen renders; the television surface renders the same states. */
sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    /**
     * [notice] is what went wrong while the library on screen stayed
     * usable — a refresh that could not reach the channel, over a catalog
     * already on disk. Said rather than swallowed, because otherwise a
     * viewer has no way to tell a library that is current from one that
     * stopped updating days ago.
     */
    data class Ready(val shelves: List<Shelf>, val notice: String? = null) : CatalogUiState
    data object Empty : CatalogUiState
    data class Failed(val message: String) : CatalogUiState
}

/**
 * One row of the library: films, shows or courses, one card each.
 *
 * A shelf holds [Entry] rather than sets, because two of the three shelves
 * are not lists of sets at all — a show and a course are cards that open.
 */
data class Shelf(val title: String, val entries: List<Entry>)

/**
 * The collection a key names, or `null` when the shelves are not here yet
 * or no longer hold it.
 *
 * A screen keeps the key rather than the collection, so that what a saved
 * position restores to is a short string and not a tree of a few hundred
 * sets. `null` while the library is still loading is the useful half: the
 * same key resolves a moment later, which is what makes a process killed
 * inside a course come back to that course.
 */
fun CatalogUiState.collection(key: String): Entry.Collection? = (this as? CatalogUiState.Ready)
    ?.shelves
    ?.asSequence()
    ?.flatMap { it.entries }
    ?.filterIsInstance<Entry.Collection>()
    ?.find { it.key == key }
