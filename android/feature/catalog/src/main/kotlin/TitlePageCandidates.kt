package catalog

import model.MediaSet

/**
 * Every film across every shelf, unwrapped from its card.
 *
 * What a film's own page ranks Similar against and checks for a franchise
 * with (`franchisesIn` needs the whole library's films, not just its
 * shelf's own genre — a franchise's other half often sits on a different
 * shelf entirely). Pulled out as its own function once a title page needed
 * it twice, rather than walking the shelves again for the second.
 */
fun filmsOf(shelves: List<Shelf>): List<MediaSet> =
    shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Film>().map { it.set }.toList()

/** Every show across every shelf — what a show's own page ranks Similar against. */
fun showsOf(shelves: List<Shelf>): List<Entry.Collection> =
    shelves
        .asSequence()
        .flatMap { it.entries }
        .filterIsInstance<Entry.Collection>()
        .filter { it.kind == CollectionKind.SHOW }
        .toList()
