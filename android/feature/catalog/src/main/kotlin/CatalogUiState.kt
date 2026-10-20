package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot
import playback.FilmPreloadRow

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
        val watch: WatchSnapshot = WatchSnapshot.Empty,
        val notice: String? = null,
        val refreshing: Boolean = false,
        /** Sets on this device's disk in full — the "offline" badge's own source. */
        val heldIds: Set<String> = emptySet(),
    ) : CatalogUiState

    data object Empty : CatalogUiState

    /** A kids profile over a library with nothing its own [limit] allows yet. */
    data class KidsEmpty(
        val limit: Int,
    ) : CatalogUiState {
        /** The web's own empty-shelf sentence, naming the kid's limit. */
        val message: String get() = "Nothing rated FSK $limit or under yet."
    }

    data class Failed(
        val message: String,
    ) : CatalogUiState
}

/**
 * One department of the library: films, shows or courses, one card each.
 *
 * A shelf holds [Entry] rather than sets, because most shelves are not lists
 * of sets at all — a show and a course are cards that open.
 */
data class Shelf(
    val department: Department,
    val entries: List<Entry>,
) {
    /** What the masthead calls this shelf — for display only; [department] is what tells shelves apart. */
    val title: String get() = department.label
}

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
fun CatalogUiState.collection(key: String): Entry.Collection? =
    (this as? CatalogUiState.Ready)
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
fun CatalogUiState.mediaSet(setId: String): MediaSet? =
    (this as? CatalogUiState.Ready)
        ?.shelves
        ?.asSequence()
        ?.flatMap { it.entries }
        ?.flatMap { entry ->
            when (entry) {
                is Entry.Film -> {
                    sequenceOf(entry.set)
                }

                is Entry.Collection -> {
                    entry.divisions
                        .asSequence()
                        .flatMap(Division::walk)
                        .flatMap { it.items.asSequence() }
                }
            }
        }?.find { it.setId == setId }

/**
 * Titles this device holds in full — the Preloads page's own "On this
 * device" section, read off [CatalogUiState.Ready.heldIds] rather than a
 * second held-set query of its own: the shelves' film cards (a documentary
 * included — TMDB shelves it under the same kind), then the finished
 * episodes, in show and episode order. Episodes are resolved through
 * [mediaSet], so a profile whose catalogue lacks one never lists it. Empty
 * while the library is still loading, the same as [heldIdsOrEmpty] and its
 * neighbours.
 */
fun CatalogUiState.heldFilms(): List<MediaSet> {
    val ready = this as? CatalogUiState.Ready ?: return emptyList()
    val films = ready.shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set }.filter { it.setId in ready.heldIds }
    val episodes = ready.heldIds.mapNotNull(::mediaSet).filter { it.kind == Kind.EPISODE }.sortedWith(compareBy({ it.show }, { it.season }, { it.episodeFirst }))
    return films.toList() + episodes
}

/**
 * Whether the menu offers Preloads: while something is queued, or while this
 * profile's catalogue resolves a title held on the device — so finished
 * films and episodes can always be found and removed. A kids profile whose
 * only held title is one its catalogue hides gets no entry.
 */
fun CatalogUiState.showsPreloads(queueCount: Int): Boolean = queueCount > 0 || heldFilms().isNotEmpty()

/**
 * Only the rows this profile's own catalogue can resolve — a kids
 * profile's shelves never carry a title above its own age limit, so a
 * grown-up's preload for one must not surface here either: not its title
 * in a "Queued · after" label, not its row on the Preloads page, not its
 * count in the menu badge. A grown-up's own catalogue resolves every
 * title, so nothing changes there.
 */
fun CatalogUiState.resolvableQueueRows(rows: List<FilmPreloadRow>): List<FilmPreloadRow> =
    rows.filter { mediaSet(it.setId) != null }
