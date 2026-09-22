package catalog

import model.MediaSet

/** How many plates a row holds before the rest is left to its own shelf. */
const val HOME_ROW_LIMIT = 6

/**
 * One row of the start page, and the shelf it was drawn from.
 *
 * [shelf] is carried so that "See all" can put the viewer on the whole of
 * it rather than on a search for its name: the row is a window onto a
 * shelf, not a category of its own.
 */
data class HomeRow(val title: String, val shelf: String, val entries: List<Entry>)

/**
 * What the start page shows, decided before anything is drawn.
 *
 * A library of six hundred titles has two facts about it worth landing on:
 * what arrived recently, and what was already underway. Only the first is
 * answerable here. The phone keeps no watch state at all — the core exposes
 * nothing that touches progress — so Continue and Next up have no source to
 * read and are absent rather than empty. A row that is always empty teaches
 * a viewer to ignore the place it sits in.
 *
 * Arrival, not release. A film made in 1975 and uploaded on Tuesday is new
 * here, because this is a catalogue of one household's own library and the
 * question it answers is what turned up, not what came out.
 */
fun homeRowsOf(shelves: List<Shelf>, limit: Int = HOME_ROW_LIMIT): List<HomeRow> =
    shelves
        .map { shelf -> HomeRow(latestTitleFor(shelf.title), shelf.title, newestFirst(shelf.entries, limit)) }
        .filter { it.entries.isNotEmpty() }

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
