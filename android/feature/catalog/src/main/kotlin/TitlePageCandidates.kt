package catalog

import model.MediaSet

/**
 * Every film on the Movies shelf, unwrapped from its card.
 *
 * What a film's own page ranks Similar against and checks for a franchise
 * with — the web's own `similarTo`/`franchisesIn` read `library.movies`
 * alone (`app.js`), and reading only the Movies shelf here rather than every
 * shelf's own `Entry.Film` is what keeps a documentary's own standalone
 * singles, shelved as films are, out of a film's Similar row. Pulled out as
 * its own function once a title page needed it twice, rather than walking
 * the shelves again for the second.
 */
fun filmsOf(shelves: List<Shelf>): List<MediaSet> =
    shelves.firstOrNull { it.title == "Movies" }?.entries.orEmpty().filterIsInstance<Entry.Film>().map { it.set }

/** Every show across every shelf — what a show's own page ranks Similar against. */
fun showsOf(shelves: List<Shelf>): List<Entry.Collection> =
    shelves
        .asSequence()
        .flatMap { it.entries }
        .filterIsInstance<Entry.Collection>()
        .filter { it.kind == CollectionKind.SHOW }
        .toList()
