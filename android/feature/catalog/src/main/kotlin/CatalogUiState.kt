package catalog

import model.MediaSet

/** What the catalog screen renders; the television surface renders the same states. */
sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    /**
     * [notice] is what went wrong while the library on screen stayed
     * usable — a refresh that could not reach the channel, over a catalog
     * already on disk. Said rather than swallowed, because otherwise a
     * viewer has no way to tell a library that is current from one that
     * stopped updating days ago.
     *
     * [refreshing] is a read of the channel in flight over shelves that are
     * still whole. A reload replaces the library rather than building the
     * first one, so it has something to show throughout, and blanking the
     * shelves to a spinner would take a library away from whoever is
     * looking at it for as long as the network takes.
     */
    data class Ready(
        val shelves: List<Shelf>,
        val notice: String? = null,
        val refreshing: Boolean = false,
    ) : CatalogUiState
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

/**
 * The set an id names, wherever it sits — a film on a shelf, or an episode
 * or lesson somewhere inside a collection.
 *
 * Kept as a lookup for the same reason [collection] is: a screen that has
 * opened a title saves the id, which is a short string, and resolves it
 * again from whatever the library currently holds. `null` while the shelves
 * are still loading is the useful half — the same id resolves a moment
 * later, which is what brings a killed process back to the title it was on.
 */
fun CatalogUiState.mediaSet(setId: String): MediaSet? = (this as? CatalogUiState.Ready)
    ?.shelves
    ?.asSequence()
    ?.flatMap { it.entries }
    ?.flatMap { entry ->
        when (entry) {
            is Entry.Film -> sequenceOf(entry.set)
            is Entry.Collection -> entry.divisions
                .asSequence()
                .flatMap(Division::walk)
                .flatMap { it.items.asSequence() }
        }
    }
    ?.find { it.setId == setId }
