package catalog

import model.MediaSet

/**
 * What a shelf holds.
 *
 * A card never nests: it stands for a whole film, show or course, and
 * opening it is what reveals the shape inside. That is the same division
 * the web player draws, and the reason the Series shelf is a list of shows
 * rather than of every episode anyone ever uploaded.
 */
sealed interface Entry {

    /** A film. There is nothing inside it, so its card plays. */
    data class Film(val set: MediaSet) : Entry

    /**
     * A show or a course: one card for the whole of it.
     *
     * [count] is what the collection holds at any depth and [chapters] how
     * many folders actually hold something — a folder of folders is
     * structure, not a chapter, and counting it would tell a viewer the
     * course has more parts than it has.
     */
    data class Collection(
        val key: String,
        val kind: CollectionKind,
        val name: String,
        val posterPath: String?,
        val count: Int,
        val chapters: Int,
        val divisions: List<Division>,
    ) : Entry
}

/**
 * Whether a collection is a show or a course.
 *
 * They are built the same way and count differently: a show is measured in
 * the episodes it holds, a course in the chapters someone works through.
 * Carried here rather than inferred from a shelf's title, which is a label
 * that can be renamed.
 */
enum class CollectionKind { SHOW, COURSE }

/**
 * One level inside a collection: a season, a chapter, or a folder of a
 * course that has folders of its own.
 *
 * Both lists are kept rather than flattened. A course runs from one folder
 * deep to four, and flattening it produces a row of sibling headings that
 * each repeat their parents and say nothing about how the course is built.
 */
data class Division(
    val title: String,
    val season: Int?,
    val items: List<MediaSet>,
    val children: List<Division>,
)

/** Every division under these, each before the ones beneath it. */
fun Division.walk(): Sequence<Division> = sequence {
    yield(this@walk)
    for (child in children) yieldAll(child.walk())
}

/** The first set anywhere under [divisions], in the order they display. */
fun firstItemOf(divisions: List<Division>): MediaSet? =
    divisions.asSequence().flatMap { it.walk() }.firstNotNullOfOrNull { it.items.firstOrNull() }

/**
 * The division [names] leads to, or `null` when it names a folder that is
 * not there — which is what a stale saved position produces.
 *
 * An empty trail is the collection itself, which is not a division and has
 * to be stood in for, so that every level of a course is one shape the
 * screen can render.
 */
fun divisionAt(divisions: List<Division>, names: List<String>): Division? {
    var here = Division(title = "", season = null, items = emptyList(), children = divisions)
    for (name in names) {
        here = here.children.find { it.title == name } ?: return null
    }
    return here
}
