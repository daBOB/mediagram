package catalog

import data.ProgressPoint
import data.ResumePoint
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/** How many plates a row holds before the rest is left to its own shelf. */
const val HOME_ROW_LIMIT = 6

/**
 * One row of the start page. [seeAll] is the shelf "See all" opens, or
 * `null` while the row has nowhere to send it yet — Continue, until the
 * kept-shelves phase gives it a tab of its own. [total] is how much the row
 * is a window onto, for the heading: the plates on screen cannot say it on
 * their own.
 */
data class HomeRow(val title: String, val seeAll: String?, val total: Int, val content: RowContent)

/**
 * What a row draws. A show or a course arriving on Latest is a card for the
 * whole of it; an episode offered under Continue or Next up is not — it is
 * one set with a caption about this viewer's own place in it. Two shapes
 * rather than bending [Entry.Film] over an episode that is not a film.
 */
sealed interface RowContent {
    data class Entries(val entries: List<Entry>) : RowContent
    data class Sets(val cards: List<SetCard>) : RowContent
}

/** One set on Continue or Next up: what to play, what to say under it, and its own mark. */
data class SetCard(val set: MediaSet, val caption: String, val progress: Float?, val watched: Boolean)

/**
 * The rows the start page shows, from the library and this viewer's own
 * watch state — Continue and Next up first, then the three Latest shelves,
 * in that order because Continue and Next up are unlikely to correspond to
 * what a viewer opened this page to reload.
 */
fun homeRowsOf(shelves: List<Shelf>, watch: WatchSnapshot, limit: Int = HOME_ROW_LIMIT): List<HomeRow> {
    val collections = shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Collection>().toList()
    val underway = underwayOf(collections, indexById(shelves), watch, limit)
    val positions = watch.progress.associateBy { it.setId }
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }

    val rows = mutableListOf<HomeRow>()

    if (underway.continues.isNotEmpty()) {
        rows += HomeRow(
            title = "Continue",
            seeAll = null,
            total = underway.continuesTotal,
            content = RowContent.Sets(
                underway.continues.map { set -> setCard(set, resumeLine(positions[set.setId]), positions, watchedIds) },
            ),
        )
    }

    if (underway.nextUp.isNotEmpty()) {
        rows += HomeRow(
            title = "Next up",
            seeAll = "Series",
            total = underway.nextUpTotal,
            content = RowContent.Sets(
                underway.nextUp.map { entry ->
                    // The captions differ within the row on purpose: one card
                    // is where the viewer stopped, the next is what follows
                    // an episode they finished.
                    val caption = if (entry.resume) resumeLine(positions[entry.set.setId]) else "Next up"
                    setCard(entry.set, caption, positions, watchedIds)
                },
            ),
        )
    }

    for (shelf in shelves) {
        rows += HomeRow(
            title = latestTitleFor(shelf.title),
            seeAll = shelf.title,
            total = shelf.entries.size,
            content = RowContent.Entries(newestFirst(shelf.entries, limit)),
        )
    }

    return rows
}

private fun setCard(
    set: MediaSet,
    caption: String,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
): SetCard {
    val progress = ResumePoint.watchedFraction(positions[set.setId]?.let { ProgressPoint(it.at, it.duration) })
    return SetCard(set, caption, progress?.toFloat(), set.setId in watchedIds)
}

/** Every set anywhere in [shelves], keyed by id — a film as much as an episode or a lesson. */
private fun indexById(shelves: List<Shelf>): Map<String, MediaSet> {
    val byId = HashMap<String, MediaSet>()
    for (shelf in shelves) {
        for (entry in shelf.entries) {
            when (entry) {
                is Entry.Film -> byId[entry.set.setId] = entry.set
                is Entry.Collection -> for (division in entry.divisions.asSequence().flatMap { it.walk() }) {
                    for (set in division.items) byId[set.setId] = set
                }
            }
        }
    }
    return byId
}

/**
 * A row's name, from the shelf it draws on.
 *
 * The shelf titles are the vocabulary a viewer already reads in the
 * masthead, so the rows borrow them rather than introducing a second set of
 * words for the same three things.
 */
private fun latestTitleFor(shelf: String): String = "Latest $shelf".lowercase()
    .replaceFirstChar(Char::uppercase)

/** Newest first, on a copy — the shelf keeps the order it was built in. */
private fun newestFirst(entries: List<Entry>, limit: Int): List<Entry> =
    entries.sortedByDescending(::arrivedAt).take(limit)

/**
 * When an entry last gained something.
 *
 * A collection is dated by its newest member, not its first: a series still
 * being uploaded keeps its place on the row, and one finished two years ago
 * does not hold the top of it for having been started recently.
 */
private fun arrivedAt(entry: Entry): Long = when (entry) {
    is Entry.Film -> entry.set.addedAt
    is Entry.Collection -> entry.divisions
        .asSequence()
        .flatMap { it.walk() }
        .flatMap { it.items.asSequence() }
        .maxOfOrNull(MediaSet::addedAt)
        ?: 0
}
