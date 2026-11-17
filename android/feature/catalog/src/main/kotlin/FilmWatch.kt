package catalog

import model.MediaSet
import model.Progress
import model.WatchSnapshot

/*
 * Watch state for whatever a card stands for — the web's `film-watch.js` and
 * `film-session.js`. A film held more than once is played when any copy is,
 * offline when any copy is held, on My List when any copy is listed, and its
 * card shows the newest place any copy was left at; anything else reads as
 * itself. The watch state stays one row per file and knows nothing of this.
 */

/** Every copy's set id to the film it belongs to, across [shelves] — films held more than once only. */
fun filmIndexOf(shelves: List<Shelf>): Map<String, MediaSet> =
    filmIndexOf(shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set })

/** This snapshot with Continue's rows as [continueRows] leaves them: a film held more than once appears once. */
fun WatchSnapshot.continuing(shelves: List<Shelf>): WatchSnapshot {
    val films = filmIndexOf(shelves)
    if (films.isEmpty()) return this
    val watchedAt = watched.associate { it.setId to it.finishedAt }
    return copy(progress = continueRows(progress.sortedByDescending(Progress::updatedAt), films::get, watchedAt::get))
}

/** [watchedIds] with every copy of a film among [films] counted once any copy is watched — for a pick that asks by id. */
fun filmsWatched(
    watchedIds: Set<String>,
    films: Iterable<MediaSet>,
): Set<String> =
    watchedIds + films.filter { playedOf(it, watchedIds) }.flatMap { versionsOf(it).map(MediaSet::setId) }

/** Whether the title a card stands for has been watched to the end. */
fun playedOf(
    set: MediaSet,
    watchedIds: Set<String>,
): Boolean = versionsOf(set).any { it.setId in watchedIds }

/** Whether this device holds the title a card stands for in full. */
fun heldOf(
    set: MediaSet,
    heldIds: Set<String>,
): Boolean = versionsOf(set).any { it.setId in heldIds }

/** Whether the title a card or page stands for is on My List. */
fun listedOf(
    set: MediaSet,
    watchlist: Collection<String>,
): Boolean = versionsOf(set).any { it.setId in watchlist }

/** The progress row a card's rule and caption are drawn from: for a film held more than once, [filmPosition]. */
fun cardPosition(
    set: MediaSet,
    positions: Map<String, Progress>,
    watchedAt: (String) -> Long?,
): Progress? = if (set.versions.isEmpty()) positions[set.setId] else filmPosition(set, positions::get, watchedAt)

/** Where [version] of [film] starts from, by the per-cut rule; anything else from its own row. */
fun positionFor(
    version: MediaSet,
    film: MediaSet?,
    watch: WatchSnapshot,
): Progress? {
    if (film == null || film.versions.isEmpty()) return watch.progress.find { it.setId == version.setId }
    val positions = watch.progress.associateBy(Progress::setId)
    val watchedAt = watch.watched.associate { it.setId to it.finishedAt }
    return versionPosition(version, film, positions::get, watchedAt::get)
}

/** Ids to sets, dropping any the catalog no longer holds; a copy of a film shows as the film — `setsFor` in `film-session.js`. */
fun CatalogUiState.setsFor(ids: List<String>): List<MediaSet> = asFilms(ids.mapNotNull(::mediaSet), ::filmOf)
