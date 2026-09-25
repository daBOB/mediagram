package catalog

import data.ProgressPoint
import data.ResumePoint
import model.KidsVerdict
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import model.kidsVerdict

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
enum class KeptKind(
    val label: String,
    val empty: String,
) {
    CONTINUE("Continue", "Nothing started yet."),
    WATCHLIST("Watchlist", "Nothing on the list."),
    COLLECTIONS("Collections", "No lists yet."),
    KIDS("Kids", "Nothing rated FSK 12 or younger, and nothing marked. An unrated title can be marked with Kids in the player."),
}

/**
 * Every title with a position worth resuming, newest first — `viewContinue`
 * in app.js. Unlike the start page's Continue row, nothing here is held back
 * for Next up: this is the whole shelf its tab promises.
 */
fun continueWall(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
): List<MediaSet> {
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
fun watchlistWall(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
): List<MediaSet> = setsFor(shelves, watch.watchlist)

/**
 * What a child may watch — `kidsShelf` in `age-rating.js`, which `viewKids`
 * draws: films and shows rated FSK 12 or younger, then whatever unrated was
 * marked by hand. The rules are [model.kidsVerdict]'s.
 */
data class KidsShelf(
    val films: List<Entry.Film>,
    val series: List<Entry.Collection>,
    val byHand: List<MediaSet>,
) {
    val total: Int get() = films.size + series.size + byHand.size
}

/**
 * A show is rated as a show, so its first episode answers for all of it, as
 * it does on the web. A course has no rating and only reaches the shelf a
 * lesson at a time, by hand. Marked titles keep the core's own
 * newest-marked-first order, as [watchlistWall] does; a mark on anything
 * rated no longer counts, since its rating already decided.
 */
fun kidsShelf(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
): KidsShelf {
    val entries = shelves.flatMap(Shelf::entries)
    val films = entries.filterIsInstance<Entry.Film>().filter { it.set.kidsVerdict() == KidsVerdict.SAFE }
    val series =
        entries.filterIsInstance<Entry.Collection>().filter { collection ->
            collection.kind == CollectionKind.SHOW &&
                firstItemOf(collection.divisions)?.kidsVerdict() == KidsVerdict.SAFE
        }
    val byHand = setsFor(shelves, watch.kids).filter { it.kidsVerdict() == KidsVerdict.UNRATED }
    return KidsShelf(films, series, byHand)
}

/** Ids to sets, quietly dropping any the catalog no longer holds — `setsFor` in app.js. */
private fun setsFor(
    shelves: List<Shelf>,
    ids: List<String>,
): List<MediaSet> {
    val byId = indexById(shelves)
    return ids.mapNotNull { byId[it] }
}

private fun Progress.toProgressPoint(): ProgressPoint = ProgressPoint(at, duration)
