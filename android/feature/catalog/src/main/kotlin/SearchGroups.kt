package catalog

import model.Kind
import model.ListOfSets
import model.PersonHit
import uniffi.mediagram_core.SearchHit

/** Which part of a grouped search result a viewer has narrowed the page to. */
enum class SearchFilter { ALL, MOVIES, SERIES, TUTORIALS, PEOPLE, COLLECTIONS }

/** A franchise or a hand-built list matched by name — ported from `destination` in `collections-page.js`. */
data class SearchDestination(val filter: SearchFilter, val name: String, val itemCount: Int, val art: String?, val href: String)

/**
 * Search grouped the way the web's `search-view.js` groups it: films as
 * posters, matched shows as show cards (their episodes rolled up rather
 * than listed twice), the matching episodes and lessons as rows, people from
 * the index's credits, and collections — franchises and the viewer's own
 * lists whose name holds every word of the query.
 *
 * [filters] only names a kind with at least one result, [SearchFilter.ALL]
 * prepended only once two or more other kinds do — the same gate that hides
 * the web's filter pills below three kinds ("all" plus one is nothing to
 * filter).
 */
data class SearchGroups(
    val films: List<SearchRow>,
    val matchedShows: List<Entry.Collection>,
    val episodes: List<SearchRow>,
    val lessons: List<SearchRow>,
    val people: List<VisiblePerson>,
    val collections: List<SearchDestination>,
    val filters: List<Pair<SearchFilter, Int>>,
)

fun searchGroupsOf(
    query: String,
    catalogState: CatalogUiState,
    hits: List<SearchHit>,
    peopleHits: List<PersonHit>,
    franchises: List<Franchise>,
    lists: List<ListOfSets>,
): SearchGroups {
    val shelves = (catalogState as? CatalogUiState.Ready)?.shelves.orEmpty()
    val rows = searchRowsOf(hits, catalogState)
    val films = rows.filter { it.set.kind == Kind.MOVIE }
    val episodes = rows.filter { it.set.kind == Kind.EPISODE }
    val lessons = rows.filter { it.set.kind == Kind.TUTORIAL || it.set.kind == Kind.DOCUMENT }
    val showNames = episodes.mapNotNullTo(LinkedHashSet()) { it.set.show }
    val matchedShows = shelves.asSequence().flatMap { it.entries }.filterIsInstance<Entry.Collection>()
        .filter { it.name in showNames }.toList()

    val people = visiblePeople(
        peopleHits.map { PersonCandidate(it.personId, it.name, it.portraitPath, it.titleKeys) },
        isVisible = titlesByKey(shelves),
    )

    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    fun named(name: String): Boolean = words.isNotEmpty() && words.all { name.lowercase().contains(it) }
    val collections = buildList {
        franchises.filter { named(it.name) }.forEach {
            add(SearchDestination(SearchFilter.COLLECTIONS, it.name, it.films.size, it.art, "tmdb-${it.id}"))
        }
        lists.filter { named(it.name) }.forEach {
            add(SearchDestination(SearchFilter.COLLECTIONS, it.name, it.items.size, null, it.id))
        }
    }

    val counts = listOf(
        SearchFilter.MOVIES to films.size,
        SearchFilter.SERIES to episodes.size,
        SearchFilter.TUTORIALS to lessons.size,
        SearchFilter.PEOPLE to people.size,
        SearchFilter.COLLECTIONS to collections.size,
    ).filter { it.second > 0 }
    val total = films.size + episodes.size + lessons.size + people.size + collections.size
    val filters = if (counts.size >= 2) listOf(SearchFilter.ALL to total) + counts else counts

    return SearchGroups(films, matchedShows, episodes, lessons, people, collections, filters)
}
