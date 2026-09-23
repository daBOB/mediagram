package model

/**
 * Where a profile is in one title. `at`/`duration` are seconds, never a
 * percentage — a set's runtime can be unknown, and a percentage recorded
 * against an unknown length cannot be turned back into a position to seek
 * to. Mirrors the core's `ProgressRow` so nothing above [data.WatchStateRepository]
 * depends on the generated bindings directly.
 */
data class Progress(val setId: String, val at: Double, val duration: Double?, val updatedAt: Long)

/** One title a profile watched to the end, and when. */
data class Watched(val setId: String, val finishedAt: Long)

/**
 * A named group of sets a profile collected — a collection's contents.
 * `id` is the core's own row id; renaming or deleting one by it is
 * deferred to the phase that first shows collections in the UI.
 */
data class ListOfSets(val id: String, val name: String, val items: List<String>)

/**
 * Who is watching. A name is the cross-device identity a sync round
 * matches on; `id` is this device's local shorthand for it and means
 * nothing on another device.
 */
data class Profile(val id: String, val name: String)

/**
 * One profile's everything, in one read — progress, what has been finished,
 * the watchlist, which sets are marked for kids, and the collections built
 * on top of the library. [kids] is shared across every profile on this
 * account rather than one profile's own list; the core keeps it that way
 * and this only carries it through unchanged.
 */
data class WatchSnapshot(
    val progress: List<Progress>,
    val watched: List<Watched>,
    val watchlist: List<String>,
    val kids: List<String>,
    val collections: List<ListOfSets>,
) {
    companion object {
        val Empty = WatchSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
