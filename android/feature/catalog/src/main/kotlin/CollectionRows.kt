package catalog

import model.Kind
import model.MediaSet

/**
 * One line of what is inside a show or a course: a heading for a division,
 * or a set under it.
 *
 * Public rather than module-private: a show's Episodes tab renders the
 * same rows for the one season its picker shows, on both surfaces, and a
 * set of episodes is not something worth two rendering paths.
 */
sealed interface CollectionRow {
    val depth: Int

    data class Heading(
        override val depth: Int,
        val title: String,
    ) : CollectionRow

    data class Item(
        override val depth: Int,
        val set: MediaSet,
        val position: Int,
    ) : CollectionRow
}

/**
 * The divisions and their sets, in reading order, each with how deep it
 * sits.
 *
 * A division holding nothing but folders still gets its heading: it is how
 * the course was built, and dropping it would join two levels that are not
 * the same level.
 */
fun rowsOf(
    divisions: List<Division>,
    depth: Int = 0,
): List<CollectionRow> =
    divisions.flatMap { division ->
        buildList {
            add(CollectionRow.Heading(depth, division.title))
            division.items.forEachIndexed { index, set -> add(CollectionRow.Item(depth, set, index + 1)) }
            addAll(rowsOf(division.children, depth + 1))
        }
    }

/**
 * How much a course holds, as its page head says it — `extentOf` in the
 * web's `course-view.js`: "n lessons · m documents", or just the lessons
 * where there are none, spelled as `countOf` spells. A documentary
 * collection is shelved and opened as a course and counts documentaries
 * instead, `countsUnder`'s own rule.
 */
fun courseExtentOf(divisions: List<Division>): String {
    val sets = divisions.flatMap { top -> top.walk().flatMap { it.items }.toList() }
    val documents = sets.count { it.kind == Kind.DOCUMENT }
    val noun = if (sets.any { it.kind == Kind.DOCUMENTARY }) "documentary" else "lesson"
    return listOfNotNull(spelledCountOf(sets.size - documents, noun), documents.takeIf { it > 0 }?.let { spelledCountOf(it, "document") })
        .joinToString(" · ")
}

/** `Season 2 · eight episodes` — one choice in a show's season picker, as the option in `series-page.js`'s select words it. */
fun seasonOptionOf(division: Division): String = "${division.title} · ${spelledCountOf(division.items.size, "episode")}"
