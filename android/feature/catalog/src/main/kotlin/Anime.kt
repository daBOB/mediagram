package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot

/**
 * The title the Anime shelf carries — named once here for the same reason
 * [DOCUMENTARIES] is (`TitlePageCandidates.kt`): routing a department to its
 * own page, the home rows that leave it out of "Latest", and the hero/scroll
 * plumbing that needs to tell it apart from a plain shelf.
 */
const val ANIME = "Anime"

/**
 * Anime series (seasons kept, grouped by show) and anime films, pulled out
 * of the catalog ahead of [shelvesOf]'s own split — a pure Kotlin port of
 * the web's `groupAnime` (`departments.js`).
 */
data class AnimeLibrary(
    val shows: List<Entry.Collection>,
    val films: List<MediaSet>,
)

/**
 * Groups every set already marked [MediaSet.anime] into its own library.
 *
 * Keyed under its own `ANIME/` prefix rather than `collections()`'s own
 * "SHOW/<name>" — the same reason [groupDocumentaries] re-keys its own
 * folders: a live-action show and an anime one are free to share a name, and
 * [CatalogUiState.collection] resolves a key across every shelf, so two
 * different collections answering to the same key would mean whichever
 * shelf is searched first wins the tap.
 */
fun groupAnime(sets: List<MediaSet>): AnimeLibrary {
    val episodes = sets.filter { it.kind == Kind.EPISODE }
    val films = sets.filter { it.kind == Kind.MOVIE }
    val shows = collections(episodes, CollectionKind.SHOW, "Unknown show")
        .map { it.copy(key = "ANIME/${it.name}") }
    return AnimeLibrary(shows = shows, films = films.sortedWith(compareBy(NATURAL) { it.title }))
}

/**
 * The Anime department's opening page — ported from `renderAnimeDept` in
 * the web's `anime-department.js`. `null` for an empty library, the same
 * empty state a department page falls back to.
 *
 * [continuing] is the general Continue list narrowed to `anime: true` sets —
 * not to [library]'s own shows, the same reason [documentariesDepartmentOf]
 * narrows by kind instead of by its own folders: an anime film is a
 * [Kind.MOVIE] the plain Continue computation cannot otherwise single out.
 * [nextUp] needs no such narrowing — it already comes from [underwayOf]
 * walking only [library]'s own, already-anime-only shows.
 */
data class AnimeDepartment(
    val showCount: Int,
    val filmCount: Int,
    val lead: MediaSet?,
    val continuing: List<MediaSet>,
    val nextUp: List<NextUpEntry>,
    val shows: List<Entry.Collection>,
    val films: List<MediaSet>,
)

fun animeDepartmentOf(
    library: AnimeLibrary,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
): AnimeDepartment? {
    if (library.shows.isEmpty() && library.films.isEmpty()) return null
    val watchedIds = watch.watched.mapTo(HashSet()) { it.setId }
    val showLeads = library.shows.mapNotNull { firstItemOf(it.divisions) }
    val unwatched = (library.films + showLeads).filterNot { it.setId in watchedIds }
    val byPopularity = unwatched.sortedByDescending { it.popularity ?: 0.0 }
    val lead = byPopularity.firstOrNull { it.backdropPath != null }
    val underway = underwayOf(library.shows, byId, watch, DEPARTMENT_ROW)
    return AnimeDepartment(
        showCount = library.shows.size,
        filmCount = library.films.size,
        lead = lead,
        continuing = underway.continues.filter(MediaSet::anime),
        nextUp = underway.nextUp,
        shows = library.shows,
        films = library.films.sortedByDescending(MediaSet::addedAt),
    )
}
