package catalog

import data.ProgressPoint
import data.ResumePoint
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/**
 * The masthead's four kept entries — Continue, Watchlist, Collections, Kids
 * — over the catalog's own shelves and this viewer's watch state. A port of
 * `app.js`'s `KEPT` map and its four `view*` functions: the label, the empty
 * text and the ordering are read from there, since a viewer moving between
 * the two surfaces should find the same four shelves saying the same things.
 *
 * Collections carries no function of its own here — it is a shelf of lists,
 * not of titles, and [model.WatchSnapshot.collections] is already everything
 * a lists screen needs, in the order the core keeps it (`created_at`).
 */
enum class KeptKind(val label: String, val empty: String) {
    CONTINUE("Continue", "Nothing started yet."),
    WATCHLIST("Watchlist", "Nothing on the list."),
    COLLECTIONS("Collections", "No lists yet."),
    KIDS("Kids", "Nothing marked yet. Open a title and press Kids in the player."),
}

/**
 * Every title with a position worth resuming, newest first — `viewContinue`
 * in app.js. Unlike the start page's Continue row, nothing here is held back
 * for Next up: this is the whole shelf its tab promises.
 */
fun continueWall(shelves: List<Shelf>, watch: WatchSnapshot): List<MediaSet> {
    val byId = indexById(shelves)
    return watch.progress
        .sortedByDescending(Progress::updatedAt)
        .filter { ResumePoint.resumeAt(it.toProgressPoint()) != null }
        .mapNotNull { byId[it.setId] }
}

/**
 * Titles marked to come back to — `viewWatchlist` in app.js. The core
 * already answers newest-added-first (`ORDER BY added_at DESC`); this only
 * resolves the ids it hands back into sets.
 */
fun watchlistWall(shelves: List<Shelf>, watch: WatchSnapshot): List<MediaSet> = setsFor(shelves, watch.watchlist)

/**
 * Titles marked for a child — `viewKids` in app.js. Newest-marked-first for
 * the same reason [watchlistWall] is: the core's own ordering, not one built
 * here.
 */
fun kidsWall(shelves: List<Shelf>, watch: WatchSnapshot): List<MediaSet> = setsFor(shelves, watch.kids)

/** Ids to sets, quietly dropping any the catalog no longer holds — `setsFor` in app.js. */
private fun setsFor(shelves: List<Shelf>, ids: List<String>): List<MediaSet> {
    val byId = indexById(shelves)
    return ids.mapNotNull { byId[it] }
}

private fun Progress.toProgressPoint(): ProgressPoint = ProgressPoint(at, duration)
