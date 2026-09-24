package catalog

import model.MediaSet

/**
 * One line of what is inside a show or a course: a heading for a division,
 * or a set under it.
 *
 * Public rather than module-private: the season screen renders the same
 * rows for the one division a season plate was opened from, and a set of
 * episodes is not something worth two rendering paths.
 */
sealed interface Row {
    val depth: Int

    data class Heading(
        override val depth: Int,
        val title: String,
    ) : Row

    data class Item(
        override val depth: Int,
        val set: MediaSet,
        val position: Int,
    ) : Row
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
): List<Row> =
    divisions.flatMap { division ->
        buildList {
            add(Row.Heading(depth, division.title))
            division.items.forEachIndexed { index, set -> add(Row.Item(depth, set, index + 1)) }
            addAll(rowsOf(division.children, depth + 1))
        }
    }
