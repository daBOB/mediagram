package catalog

/**
 * Everything tagged with one genre: the films, then the series — ported
 * from the web's `genreShelf` in `genres.js`.
 *
 * A course is never asked, the same way the web reads only `library.movies`
 * and `library.series`: a genre is a fact a provider records about a film
 * or a show, and a course has no provider entry to carry one.
 *
 * A series is tagged by its show, which every episode's [model.MediaSet.genres]
 * already carries, so its first episode answers for the whole show — the
 * same one [Entry.Collection]'s own poster is drawn from.
 */
data class GenreShelf(val films: List<Entry.Film>, val series: List<Entry.Collection>)

/**
 * [name] is matched exactly as the catalog stored it: the uploader writes
 * one spelling per genre, so there is nothing to normalise, and a looser
 * match would put a title on a shelf it was never tagged for.
 */
fun genreShelf(shelves: List<Shelf>, name: String): GenreShelf {
    val entries = shelves.asSequence().flatMap { it.entries }
    val films = entries.filterIsInstance<Entry.Film>()
        .filter { name in it.set.genres }
        .toList()
    val series = entries.filterIsInstance<Entry.Collection>()
        .filter { it.kind == CollectionKind.SHOW }
        .filter { collection -> name in (firstItemOf(collection.divisions)?.genres ?: emptyList()) }
        .toList()
    return GenreShelf(films, series)
}
