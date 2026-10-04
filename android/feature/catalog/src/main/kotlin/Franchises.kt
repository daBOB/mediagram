package catalog

import model.FranchiseInfo
import model.ListOfSets
import model.MediaSet

/** One franchise the library holds: at least two of its films, kept. */
data class Franchise(val id: Long, val name: String, val films: List<MediaSet>, val art: String?)

/**
 * The franchises the library holds, largest first; each with its films in
 * release order and the most popular one's backdrop (or poster) — ported
 * from `franchisesIn` in the web's `collections-page.js`.
 *
 * A franchise needs two held films to count: one film is a title, not a
 * collection.
 */
fun franchisesIn(movies: List<MediaSet>): List<Franchise> {
    val byId = LinkedHashMap<Long, MutableList<MediaSet>>()
    for (film in movies) {
        val id = film.collectionId ?: continue
        byId.getOrPut(id) { mutableListOf() }.add(film)
    }

    return byId.entries
        .filter { it.value.size >= 2 }
        .map { (id, films) ->
            val released = films.sortedBy { it.year ?: Int.MAX_VALUE }
            val lead =
                released
                    .filter { it.backdropPath != null || it.posterPath != null }
                    .maxByOrNull { it.popularity ?: 0.0 }
            Franchise(id, films.first().collectionName ?: "", released, lead?.backdropPath ?: lead?.posterPath)
        }
        .sortedWith(compareByDescending<Franchise> { it.films.size }.thenBy { it.name })
}

/**
 * A franchise hero's line — "25 films · 1962–2021", `renderFranchise`'s
 * count and span; a film with no known year is left out of the span, and
 * with none known at all the span is left out.
 */
fun franchiseLineOf(franchise: Franchise): String {
    val years = franchise.films.mapNotNull { it.year?.takeIf { year -> year > 0 } }
    val span = years.minOrNull()?.let { first -> "$first–${years.max()}" }
    return listOfNotNull(spelledCountOf(franchise.films.size, "film"), span).joinToString(" · ")
}

/** One franchise's own page: its films, and TMDB's introduction to it, when there is one. */
data class FranchisePage(val franchise: Franchise, val overview: String?)

/**
 * One franchise by id, over the library's held films and TMDB's own
 * overviews of every franchise the index names — ported from
 * `collections-page.js#renderFranchise`. `null` for an id the library holds
 * no franchise page under (fewer than two held films, or none at all).
 */
fun franchisePageOf(id: Long, movies: List<MediaSet>, overviews: List<FranchiseInfo>): FranchisePage? {
    val franchise = franchisesIn(movies).find { it.id == id } ?: return null
    return FranchisePage(franchise, overviews.find { it.id == id }?.overview)
}

/**
 * A list's own card art on Collections: the backdrop (or poster) of its
 * first title that has either — `renderCollectionsPage`'s
 * `setsFor(list.items).find(…)`. `null` once nothing on it is pictured, or
 * nothing it names is in this library any more.
 */
fun listArtOf(list: ListOfSets, byId: Map<String, MediaSet>): String? =
    list.items.firstNotNullOfOrNull { id -> byId[id]?.let { it.backdropPath ?: it.posterPath } }
