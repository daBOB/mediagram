package catalog

import model.MediaSet
import model.Person

/**
 * A person's page: their portrait and name, then the titles in *this*
 * profile's own library they appear in — ported from `cast.js#renderPerson`.
 *
 * `null` for both nobody by that id and somebody nobody can see: either way
 * the page says only "Nobody by that number is credited on anything in your
 * library", never a name, so a kids profile never learns who was in a title
 * it cannot open.
 *
 * @param shelves the *already profile-filtered* shelves — [catalog.CatalogUiState.Ready.shelves]
 *   as [catalog.CatalogViewModel.state] projects them for whoever is chosen,
 *   the same rule that keeps a kids profile off titles it cannot see.
 */
data class PersonPage(val person: Person, val films: List<MediaSet>, val shows: List<Entry.Collection>)

fun personPageOf(person: Person?, shelves: List<Shelf>): PersonPage? {
    if (person == null) return null
    val keys = person.titleKeys.toHashSet()
    val entries = shelves.asSequence().flatMap { it.entries }
    val films = entries.filterIsInstance<Entry.Film>().map { it.set }.filter { it.posterKey in keys }.toList()
    val shows = entries.filterIsInstance<Entry.Collection>()
        .filter { firstItemOf(it.divisions)?.posterKey in keys }
        .toList()
    return if (films.isEmpty() && shows.isEmpty()) null else PersonPage(person, films, shows)
}
