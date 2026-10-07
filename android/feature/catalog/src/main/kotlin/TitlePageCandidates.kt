package catalog

import model.MediaSet

/**
 * Every film on the Movies shelf and every anime film, unwrapped from their
 * cards.
 *
 * What a film's own page ranks Similar against and checks for a franchise
 * with — the web's own `everyFilm` reads `[...library.movies, ...library.anime.singles]`
 * (`departments.js`), and reading only these two shelves here rather than
 * every shelf's own `Entry.Film` is what keeps a documentary's own
 * standalone singles, shelved as films are, out of a film's Similar row.
 * Pulled out as its own function once a title page needed it twice, rather
 * than walking the shelves again for the second.
 */
fun everyFilm(shelves: List<Shelf>): List<MediaSet> =
    shelves
        .filter {
            when (it.department) {
                Department.MOVIES, Department.ANIME -> true
                Department.SERIES, Department.DOCUMENTARIES, Department.TUTORIALS -> false
            }
        }.flatMap { it.entries }
        .filterIsInstance<Entry.Film>()
        .map { it.set }

/** Every show across every shelf — what a show's own page ranks Similar against. */
fun showsOf(shelves: List<Shelf>): List<Entry.Collection> =
    shelves
        .asSequence()
        .flatMap { it.entries }
        .filterIsInstance<Entry.Collection>()
        .filter { it.kind == CollectionKind.SHOW }
        .toList()
