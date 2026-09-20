package catalog

import model.Kind
import model.MediaSet

private const val UNKNOWN_SHOW = "Unknown show"
private const val UNKNOWN_COURSE = "Unknown course"

/**
 * Turns a flat catalog into the three shelves a viewer expects.
 *
 * The index stores one row per set. Films are already cards; episodes and
 * lessons are not — they belong to a show or a course, and a shelf that
 * listed them one by one would show a hundred and eighty cards that each
 * repeat the same poster and say nothing about what they are part of.
 *
 * Derived here rather than inside a render loop, because the television
 * surface needs the same answer and neither surface should be where it is
 * worked out.
 */
fun shelvesOf(sets: List<MediaSet>): List<Shelf> {
    val episodes = sets.filter { it.kind == Kind.EPISODE }
    val lessons = sets.filter { it.kind == Kind.TUTORIAL }
    val films = sets.filter { it.kind == Kind.MOVIE }

    return listOf(
        Shelf("Movies", films.sortedWith(compareBy(NATURAL) { it.title }).map(Entry::Film)),
        Shelf("Series", collections(episodes, CollectionKind.SHOW, UNKNOWN_SHOW)),
        Shelf("Tutorials", collections(lessons, CollectionKind.COURSE, UNKNOWN_COURSE)),
    ).filter { it.entries.isNotEmpty() }
}

/** Groups one kind's sets by the show or course holding them, then by folder. */
private fun collections(sets: List<MediaSet>, kind: CollectionKind, fallback: String): List<Entry> {
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

private fun collectionOf(root: Division, kind: CollectionKind): Entry.Collection {
    val divisions = root.children.asSequence().flatMap { it.walk() }.toList()
    return Entry.Collection(
        key = "$kind/${root.title}",
        kind = kind,
        name = root.title,
        // Every set in a show shares its poster, so the first one that has
        // one stands for the whole of it.
        posterPath = divisions.firstNotNullOfOrNull { level ->
            level.items.firstNotNullOfOrNull(MediaSet::posterPath)
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

private class Trail(val names: List<String>, val season: Int?)

/**
 * A division while it is still being filled. The finished [Division] is
 * immutable and sorted, which is what every reader wants; building one
 * needs a shape that can be added to as sets arrive in whatever order the
 * index hands them over.
 */
private class MutableNode(val title: String, val season: Int?) {
    val items = mutableListOf<MediaSet>()
    val children = mutableListOf<MutableNode>()

    fun descend(name: String, season: Int?): MutableNode =
        children.find { it.title == name } ?: MutableNode(name, season).also(children::add)

    /**
     * Sorted on the way out, once, rather than kept sorted on every insert.
     *
     * Episodes go in the order they were meant to be watched; a set with no
     * episode number sorts last rather than first, because an unnumbered
     * extra is not episode zero.
     */
    fun freeze(): Division = Division(
        title = title,
        season = season,
        items = items.sortedWith(
            compareBy<MediaSet> { it.episodeFirst ?: Int.MAX_VALUE }.thenBy(NATURAL) { it.title },
        ),
        // Seasons by number, folders by name. A collection's divisions are
        // in practice all one or all the other; a mixture puts the numbered
        // ones first rather than interleaving them by name, which is an
        // order rather than the absence of one.
        children = children
            .map { it.freeze() }
            .sortedWith(
                compareBy<Division, Int?>(nullsLast<Int>()) { it.season }.thenBy(NATURAL) { it.title },
            ),
    )
}
