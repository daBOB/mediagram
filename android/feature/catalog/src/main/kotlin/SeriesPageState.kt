package catalog

import data.ProgressPoint
import data.ResumePoint
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/**
 * Where a show's page offers to carry on, from this viewer's real watch
 * state — wires [seriesResume] the way the web's `series-page.js` wires it
 * from `resume-point.js` and `watch-state.js`.
 */
fun seriesResumeFor(collection: Entry.Collection, watch: WatchSnapshot): SeriesResumePick? {
    val positions = watch.progress.associateBy(Progress::setId)
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    val recent = watch.progress.sortedByDescending(Progress::updatedAt).map(Progress::setId)
    return seriesResume(
        collection.divisions,
        resumeOf = { setId -> positions[setId]?.let { ResumePoint.resumeAt(ProgressPoint(it.at, it.duration)) } },
        recent = recent,
        watched = { setId -> setId in watchedIds },
    )
}

/**
 * A show or course, standing in for [similarTo]'s own `MediaSet` shape: its
 * first episode with the collection's own [Entry.Collection.key] swapped in
 * as the identity, so it compares and dedupes against other shows rather
 * than against whichever film happens to share an episode's `setId`.
 */
private fun leadOf(entry: Entry.Collection): MediaSet? = firstItemOf(entry.divisions)?.copy(setId = entry.key)

/** Whether every episode of [entry] has been watched — "seen" for a show, unlike a film's one flag. */
private fun watchedWhole(entry: Entry.Collection, watched: (String) -> Boolean): Boolean =
    entry.divisions.asSequence().flatMap { it.walk() }.flatMap { it.items.asSequence() }.all { watched(it.setId) }

/**
 * "Similar" on a show's own page — ported from `series-page.js`'s own call
 * into [similarTo]: the same franchise/genre/popularity ranking, with a show
 * counted "seen" only once every episode in it is.
 *
 * @param shows every show on the shelf Similar ranks against, [current] included
 */
fun similarShows(current: Entry.Collection, shows: List<Entry.Collection>, watched: (String) -> Boolean): List<Entry.Collection> {
    val title = leadOf(current) ?: return emptyList()
    val byKey = shows.associateBy { it.key }
    val candidates = shows.mapNotNull(::leadOf)
    val picks = similarTo(title, candidates, seen = { candidate -> byKey[candidate.setId]?.let { watchedWhole(it, watched) } == true })
    return picks.mapNotNull { byKey[it.setId] }
}
