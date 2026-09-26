package catalog

import model.MediaSet

/** One department in the Genres index. */
data class GenreIndexEntry(val name: String, val count: Int, val art: String?)

/**
 * Every genre the library holds, most titles first, each pictured by the
 * backdrop (or poster) of its most popular title that a bigger genre has not
 * already used — a page of tiles showing the same film three times reads as
 * a mistake. Ported from `genreIndex` in the web's `utility-pages.js`.
 *
 * @param titles the whole library's titles: films, and each show by its
 *   first episode (the caller's to assemble — see `titlesOf` on the web)
 */
fun genreIndex(titles: List<MediaSet>): List<GenreIndexEntry> {
    val byName = LinkedHashMap<String, MutableList<MediaSet>>()
    for (title in titles) {
        for (name in title.genres) byName.getOrPut(name) { mutableListOf() }.add(title)
    }

    val used = HashSet<String>()
    return byName.entries
        .sortedWith(compareByDescending<Map.Entry<String, List<MediaSet>>> { it.value.size }.thenBy { it.key })
        .map { (name, members) ->
            val pictured =
                members
                    .filter { it.backdropPath != null || it.posterPath != null }
                    .sortedByDescending { it.popularity ?: 0.0 }
            val lead = pictured.firstOrNull { it.setId !in used } ?: pictured.firstOrNull()
            lead?.let { used.add(it.setId) }
            GenreIndexEntry(name, members.size, lead?.backdropPath ?: lead?.posterPath)
        }
}

/**
 * The whole library's titles for the Genres index: films, and each series by
 * its first episode — ported from `titlesOf` in the web's `utility-pages.js`.
 * A course carries no provider genre and is never asked, the same reason
 * [GenreShelf] never asks one.
 */
fun allTitles(shelves: List<Shelf>): List<MediaSet> {
    val entries = shelves.asSequence().flatMap { it.entries }
    val films = entries.filterIsInstance<Entry.Film>().map { it.set }
    val series = entries.filterIsInstance<Entry.Collection>()
        .filter { it.kind == CollectionKind.SHOW }
        .mapNotNull { firstItemOf(it.divisions) }
    return (films + series).toList()
}
