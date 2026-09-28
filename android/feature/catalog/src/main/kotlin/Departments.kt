package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot

/** How many plates a department's curated rows hold — ported from `ROW` in `department-pages.js`. */
private const val DEPARTMENT_ROW = 12

/** The rating a film needs to be offered under "Acclaimed, not yet seen". */
private const val ACCLAIMED_RATING = 7.5

/**
 * The Movies department's opening page — ported from `renderMoviesDept` in
 * the web's `department-pages.js`. `null` for an empty library, the same
 * empty state a department page falls back to.
 *
 * @param films every film on the Movies shelf ([Entry.Film.set] already unwrapped)
 * @param watched whether the viewer has finished a film
 */
data class MoviesDepartment(
    val filmCount: Int,
    val hours: Int,
    /** A backdrop to lead the hero with, and its own tagline as the pull-quote — `null` when nothing unwatched has one. */
    val lead: MediaSet?,
    val featured: List<MediaSet>,
    val genres: List<GenreIndexEntry>,
    val acclaimed: List<MediaSet>,
    val recentlyAdded: List<MediaSet>,
)

fun moviesDepartmentOf(films: List<MediaSet>, watched: (String) -> Boolean): MoviesDepartment? {
    if (films.isEmpty()) return null
    val unwatched = films.filterNot { watched(it.setId) }
    val byPopularity = unwatched.sortedByDescending { it.popularity ?: 0.0 }
    val lead = byPopularity.firstOrNull { it.backdropPath != null }
    val hours = (films.sumOf { it.durationSecs ?: 0 } / 3600)
    return MoviesDepartment(
        filmCount = films.size,
        hours = hours,
        lead = lead,
        featured = byPopularity.filterNot { it.setId == lead?.setId }.take(DEPARTMENT_ROW),
        genres = genreIndex(films).take(DEPARTMENT_ROW),
        acclaimed = unwatched.filter { (it.rating ?: 0.0) >= ACCLAIMED_RATING }.sortedByDescending { it.rating }.take(DEPARTMENT_ROW),
        recentlyAdded = films.sortedByDescending(MediaSet::addedAt).take(DEPARTMENT_ROW),
    )
}

/**
 * The Documentaries department's opening page — ported from
 * `renderDocumentariesDept`. `null` for an empty library, the same empty
 * state a department page falls back to.
 *
 * Nothing here comes from a provider — no popularity, no rating, no genre —
 * so unlike Movies there is no Featured or Acclaimed row to rank; what is
 * underway, what arrived, then one row per folder and the rest.
 */
data class DocumentariesDepartment(
    val itemCount: Int,
    val lead: MediaSet?,
    /** Already-started documentaries, newest touch first — no "next up": a documentary has no next episode. */
    val continuing: List<MediaSet>,
    val recentlyAdded: List<MediaSet>,
    val collections: List<DocumentaryGroupRow>,
    /** Capped to [DEPARTMENT_ROW], same as the web's own "Standalone documentaries" row — ponytail: caps a single row rather than paging it; add a page if a library ever holds more standalone documentaries than one row shows. */
    val singles: List<MediaSet>,
)

/** One documentary folder's own row: the card ["All N"] opens, and the row's own first plates. */
data class DocumentaryGroupRow(
    val collection: Entry.Collection,
    val preview: List<MediaSet>,
)

fun documentariesDepartmentOf(
    library: DocumentaryLibrary,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
): DocumentariesDepartment? {
    val items = library.singles + library.collections.flatMap { playOrder(it.divisions) }
    if (items.isEmpty()) return null
    val byRecent = items.sortedByDescending(MediaSet::addedAt)
    // The general Continue list, any kind, narrowed to documentaries after
    // the fact — the same order `renderDocumentariesDept` reads it in: this
    // page never contributes its own folders to that list's "next up" half,
    // so passing none here is what keeps this call a plain narrowing rather
    // than a second, documentary-flavoured underway computation.
    val continuing = underwayOf(emptyList(), byId, watch, DEPARTMENT_ROW).continues.filter { it.kind == Kind.DOCUMENTARY }
    return DocumentariesDepartment(
        itemCount = items.size,
        lead = byRecent.firstOrNull { it.backdropPath != null },
        continuing = continuing,
        recentlyAdded = byRecent.take(DEPARTMENT_ROW),
        collections = library.collections.map { DocumentaryGroupRow(it, playOrder(it.divisions).take(DEPARTMENT_ROW)) },
        singles = library.singles.take(DEPARTMENT_ROW),
    )
}

/**
 * The Series or Tutorials department's opening page — ported from
 * `renderShowsDept`. `null` for an empty shelf.
 *
 * [popular] and [newEpisodes] are only offered for Series, and only once
 * there are enough shows for a row to be a selection rather than the whole
 * shelf; below [DEPARTMENT_ROW], [all] beneath them already shows every one
 * at once. The web's gate is `series && shows.length > ROW`: a course
 * library of any size gets no rows of its own.
 */
data class ShowsDepartment(
    val showCount: Int,
    val itemCount: Int,
    val lead: Entry.Collection?,
    val underway: Underway,
    val popular: List<Entry.Collection>,
    val newEpisodes: List<Entry.Collection>,
    val all: List<Entry.Collection>,
)

fun showsDepartmentOf(
    kind: Kind,
    shows: List<Entry.Collection>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
): ShowsDepartment? {
    if (shows.isEmpty()) return null
    val lead = shows.filter { firstItemOf(it.divisions)?.backdropPath != null }
        .maxByOrNull { firstItemOf(it.divisions)?.popularity ?: 0.0 }
    val underway = underwayOf(shows, byId, watch, DEPARTMENT_ROW).let {
        it.copy(continues = it.continues.filter { set -> set.kind == kind }, nextUp = it.nextUp.filter { entry -> entry.set.kind == kind })
    }
    val bigEnough = kind == Kind.EPISODE && shows.size > DEPARTMENT_ROW
    val popular =
        if (bigEnough) {
            shows.sortedByDescending { firstItemOf(it.divisions)?.popularity ?: 0.0 }.take(DEPARTMENT_ROW)
        } else {
            emptyList()
        }
    val newEpisodes = if (bigEnough) newestFirst(shows, DEPARTMENT_ROW).filterIsInstance<Entry.Collection>() else emptyList()
    return ShowsDepartment(
        showCount = shows.size,
        itemCount = shows.sumOf { it.count },
        lead = lead,
        underway = underway,
        popular = popular,
        newEpisodes = newEpisodes,
        all = shows,
    )
}
