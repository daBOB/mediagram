package catalog

import model.Kind
import model.MediaSet

private const val UNKNOWN_SHOW = "Unknown show"
private const val UNKNOWN_COURSE = "Unknown course"

/**
 * The title the Documentaries shelf carries — named once here rather than
 * spelled out at each of the handful of places that treat it differently
 * from an ordinary shelf: routing a department to its own page (`CatalogScreen.kt`,
 * `TvDepartmentPages.kt`), the pill count that sums a folder's own items
 * rather than counting folders (`ChromeCounts.kt`), and the home rows that
 * leave it out of "Latest" and "what is underway" (`HomeShelves.kt`).
 */
const val DOCUMENTARIES = "Documentaries"

/** The kinds that belong to a show or a course rather than standing alone. */
private val COLLECTED = setOf(Kind.EPISODE, Kind.TUTORIAL, Kind.DOCUMENT)

/**
 * Turns a flat catalog into the shelves a viewer expects.
 *
 * The index stores one row per set. Films are already cards; episodes and
 * lessons are not — they belong to a show or a course, and a shelf that
 * listed them one by one would show a hundred and eighty cards that each
 * repeat the same poster and say nothing about what they are part of.
 *
 * Documentaries is always present, even holding nothing — the web never
 * hides a department, and this is the one shelf a library can genuinely
 * have none of, so the one whose own empty state a viewer can actually
 * reach. The other three drop out instead: a library with no films or no
 * courses reads as one that has not been pointed at either yet, which an
 * empty department page would say far more quietly than simply not
 * offering the tab.
 *
 * Derived here rather than inside a render loop, because the television
 * surface needs the same answer and neither surface should be where it is
 * worked out.
 */
fun shelvesOf(sets: List<MediaSet>): List<Shelf> {
    val episodes = sets.filter { it.kind == Kind.EPISODE }
    // A document belongs to the course it was uploaded with, so it goes
    // into that tree beside the lessons rather than onto a shelf of its
    // own. On the film shelf a handout would read as a broken film.
    val course = sets.filter { it.kind == Kind.TUTORIAL || it.kind == Kind.DOCUMENT }
    // Everything else, which is films and any kind this build has never
    // heard of. Placing an unknown kind beats hiding it: the wrong shelf is
    // something a viewer can report, an absence is not. Documentaries has
    // its own shelf below, so it is the one recognised kind excluded here.
    val films = sets.filter { it.kind !in COLLECTED && it.kind != Kind.DOCUMENTARY }
    val documentaries = groupDocumentaries(sets.filter { it.kind == Kind.DOCUMENTARY })

    return listOfNotNull(
        Shelf("Movies", films.sortedWith(compareBy(NATURAL) { it.title }).map(Entry::Film))
            .takeIf { it.entries.isNotEmpty() },
        Shelf("Series", collections(episodes, CollectionKind.SHOW, UNKNOWN_SHOW))
            .takeIf { it.entries.isNotEmpty() },
        Shelf(DOCUMENTARIES, documentaries.collections + documentaries.singles.map(Entry::Film)),
        Shelf("Tutorials", collections(course, CollectionKind.COURSE, UNKNOWN_COURSE))
            .takeIf { it.entries.isNotEmpty() },
    )
}

/**
 * Whether any shelf actually holds something.
 *
 * Documentaries is always present even at zero, so `shelves.isEmpty()` no
 * longer says whether a library is empty — a library with nothing at all
 * still returns one, empty, Documentaries shelf. Every place that used to
 * gate "is there a library here" on the plain list reads this instead.
 */
fun List<Shelf>.hasContent(): Boolean = any { it.entries.isNotEmpty() }

/** Groups one kind's sets by the show or course holding them, then by folder. Internal rather than private: [groupDocumentaries] (`Documentaries.kt`) groups a folder of documentaries the same way a course does. */
internal fun collections(
    sets: List<MediaSet>,
    kind: CollectionKind,
    fallback: String,
): List<Entry.Collection> {
    val byName = LinkedHashMap<String, MutableNode>()

    for (set in sets) {
        val name = set.show?.takeIf(String::isNotBlank) ?: fallback
        val root = byName.getOrPut(name) { MutableNode(name, null) }
        val trail = trailOf(set)
        var node = root
        for (folder in trail.names) node = node.descend(folder, trail.season)
        node.items.add(set)
    }

    return byName.values
        .map { it.freeze() }
        .sortedWith(compareBy(NATURAL) { it.title })
        .map { root -> collectionOf(root, kind) }
}

private fun collectionOf(
    root: Division,
    kind: CollectionKind,
): Entry.Collection {
    val divisions =
        root.children
            .asSequence()
            .flatMap { it.walk() }
            .toList()
    return Entry.Collection(
        key = "$kind/${root.title}",
        kind = kind,
        name = root.title,
        // Every set in a show shares its poster, so the first one that has
        // one stands for the whole of it. The key is taken the same way and
        // for the same reason: the shows table holds one row per series, so
        // whichever episode carries the key carries the whole show's.
        posterPath =
            divisions.firstNotNullOfOrNull { level ->
                level.items.firstNotNullOfOrNull(MediaSet::posterPath)
            },
        posterKey =
            divisions.firstNotNullOfOrNull { level ->
                level.items.firstNotNullOfOrNull(MediaSet::posterKey)
            },
        count = divisions.sumOf { it.items.size },
        chapters = divisions.count { it.items.isNotEmpty() },
        divisions = root.children,
    )
}

/** Where a set sits inside its collection, as folders from the top down. */
private fun trailOf(set: MediaSet): Trail {
    set.path?.takeIf(String::isNotBlank)?.let { path ->
        return Trail(path.split("/").filter(String::isNotBlank), null)
    }
    set.chapter?.takeIf(String::isNotBlank)?.let { return Trail(listOf(it), null) }
    if (set.kind == Kind.EPISODE) {
        val season = set.season
        return Trail(listOf(season?.let { "Season $it" } ?: "Episodes"), season)
    }
    val chapter = set.season ?: 1
    return Trail(listOf("Chapter $chapter"), chapter)
}

private class Trail(
    val names: List<String>,
    val season: Int?,
)

/**
 * A division while it is still being filled. The finished [Division] is
 * immutable and sorted, which is what every reader wants; building one
 * needs a shape that can be added to as sets arrive in whatever order the
 * index hands them over.
 */
private class MutableNode(
    val title: String,
    val season: Int?,
) {
    val items = mutableListOf<MediaSet>()
    val children = mutableListOf<MutableNode>()

    fun descend(
        name: String,
        season: Int?,
    ): MutableNode = children.find { it.title == name } ?: MutableNode(name, season).also(children::add)

    /**
     * Sorted on the way out, once, rather than kept sorted on every insert.
     *
     * Episodes go in the order they were meant to be watched; a set with no
     * episode number sorts last rather than first, because an unnumbered
     * extra is not episode zero.
     */
    fun freeze(): Division =
        Division(
            title = title,
            season = season,
            items =
                items.sortedWith(
                    compareBy<MediaSet> { it.episodeFirst ?: Int.MAX_VALUE }.thenBy(NATURAL) { it.title },
                ),
            // Seasons by number, folders by name. A collection's divisions are
            // in practice all one or all the other; a mixture puts the numbered
            // ones first rather than interleaving them by name, which is an
            // order rather than the absence of one.
            children =
                children
                    .map { it.freeze() }
                    .sortedWith(
                        compareBy<Division, Int?>(nullsLast<Int>()) { it.season }.thenBy(NATURAL) { it.title },
                    ),
        )
}
