package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot

/** How many plates a department's curated rows hold — ported from `ROW` in `department-pages.js`. Internal rather than private: [animeDepartmentOf] (`Anime.kt`) caps its own underway row at the same size. */
internal const val DEPARTMENT_ROW = 12

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
    val hours = Math.round(films.sumOf { it.durationSecs ?: 0 } / 3600.0).toInt()
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
    /** One row per hand-set category, collections before singles, "Other" last — [categoryRowsOf] over [Entry]. */
    val categories: List<CategoryRow<Entry>>,
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
    // No collections: a documentary has no next episode to offer, only a
    // position to resume — the web's `renderDocumentariesDept` reads it the same way.
    val continuing = departmentUnderwayOf(emptyList(), byId, watch) { it.kind == Kind.DOCUMENTARY }.continues
    val categoryUnits: List<Entry> = library.collections + library.singles.map(Entry::Film)
    return DocumentariesDepartment(
        itemCount = items.size,
        lead = byRecent.firstOrNull { it.backdropPath != null },
        continuing = continuing,
        recentlyAdded = byRecent.take(DEPARTMENT_ROW),
        categories = categoryRowsOf(categoryUnits, ::categoryOf),
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
    /** One row per hand-set category, "Other" last — empty for Series by construction: only a course ever carries one. */
    val categories: List<CategoryRow<Entry.Collection>>,
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
    // An anime episode is still `Kind.EPISODE`, so kind alone would let it
    // leak into the Series department's own Continue row — anime left this
    // shelf entirely at `shelvesOf`, and its own department page offers the
    // same title under its own Continue watching instead (`animeDepartmentOf`).
    val underway = departmentUnderwayOf(shows, byId, watch) { set -> set.kind == kind && !set.anime }
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
        categories = categoryRowsOf(shows, ::categoryOf),
        popular = popular,
        newEpisodes = newEpisodes,
        all = shows,
    )
}
