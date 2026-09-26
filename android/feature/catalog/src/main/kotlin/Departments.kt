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
 * The Series or Tutorials department's opening page — ported from
 * `renderShowsDept`. `null` for an empty shelf.
 *
 * [popular] and [newEpisodes] are only offered once there are enough shows
 * for a row to be a selection rather than the whole shelf; below
 * [DEPARTMENT_ROW], [all] beneath them already shows every one at once.
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
    val bigEnough = shows.size > DEPARTMENT_ROW
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
