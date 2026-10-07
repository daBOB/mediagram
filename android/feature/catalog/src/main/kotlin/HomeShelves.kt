package catalog

import model.MediaSet

/** How many plates a row holds before the rest is left to its own shelf. Also TV's own row width — six plates fit a television without a rail (`TvHomeRow`'s own doc); changing it moves TV too, not just parity with the web's own row limits. */
const val HOME_ROW_LIMIT = 6

/** The web's own `POSTER_ROW_LIMIT` (`home-shelves.js:21`) — Recently Added and Latest series hold this many on Home, more than [HOME_ROW_LIMIT] (which still governs Continue/Next up and Latest courses there, and TV's own row width everywhere). */
const val HOME_POSTER_ROW_LIMIT = 8

/** One set on Continue or Next up: what to play, what to say under it, and its own mark. */
data class SetCard(val set: MediaSet, val caption: String, val progress: Float?, val watched: Boolean, val held: Boolean = false)

/**
 * What arrived, newest first, by department — the web's `latestMovies`,
 * `latestSeries` and `latestCourses` (`home-shelves.js`). Home draws the
 * series and the courses; the Latest page draws all three. Each total is the
 * whole shelf, what "See all" would open, which a cut row cannot say on its
 * own.
 */
data class Latest(
    val movies: List<Entry.Film>,
    val series: List<Entry.Collection>,
    val courses: List<Entry.Collection>,
    val moviesTotal: Int,
    val seriesTotal: Int,
    val coursesTotal: Int,
)

/**
 * [Latest] over [shelves]: films and series cut to [posterLimit], courses to
 * [limit], as the web's `homeShelves` cuts them — a course row is a list, not
 * a row of posters, and keeps the shorter limit. No anime or documentaries:
 * the web never draws either as a Latest row.
 */
fun latestOf(
    shelves: List<Shelf>,
    posterLimit: Int = HOME_POSTER_ROW_LIMIT,
    limit: Int = HOME_ROW_LIMIT,
): Latest {
    fun entriesOf(department: Department) = shelves.firstOrNull { it.department == department }?.entries.orEmpty()
    val movies = entriesOf(Department.MOVIES)
    val series = entriesOf(Department.SERIES)
    val courses = entriesOf(Department.TUTORIALS)
    return Latest(
        movies = newestFirst(movies, posterLimit).filterIsInstance<Entry.Film>(),
        series = newestFirst(series, posterLimit).filterIsInstance<Entry.Collection>(),
        courses = newestFirst(courses, limit).filterIsInstance<Entry.Collection>(),
        moviesTotal = movies.size,
        seriesTotal = series.size,
        coursesTotal = courses.size,
    )
}

/**
 * [shelves]' own collections, for [underwayOf]'s "what is underway" —
 * Documentaries excluded, the same way `home-shelves.js`'s own underway loop
 * only ever walks series, anime and tutorials: a documentary has no "next
 * episode" the way a show or a course does, so its folders never offer one
 * here even though a viewer can still resume one directly through the flat,
 * kind-agnostic Continue list [underwayOf] also returns.
 */
internal fun collectionsForNextUp(shelves: List<Shelf>): List<Entry.Collection> =
    shelves
        .asSequence()
        .filter {
            when (it.department) {
                Department.DOCUMENTARIES -> false
                Department.MOVIES, Department.SERIES, Department.ANIME, Department.TUTORIALS -> true
            }
        }.flatMap { it.entries }
        .filterIsInstance<Entry.Collection>()
        .toList()

/**
 * Every set anywhere in [shelves], keyed by id — a film as much as an
 * episode or a lesson. [continueWall], [watchlistWall] and [kidsWall] in
 * `KeptShelves.kt` resolve the same ids the same way, against the same
 * shelves. A department page needs it for the same reason [magazineHomeOf]
 * does: a Continue row resolves a progress row to its set before the
 * department's own [showsDepartmentOf] narrows it to one kind, and a
 * progress row can name a set of any kind.
 */
fun allSetsById(shelves: List<Shelf>): Map<String, MediaSet> {
    val byId = HashMap<String, MediaSet>()
    for (shelf in shelves) {
        for (entry in shelf.entries) {
            when (entry) {
                is Entry.Film -> byId[entry.set.setId] = entry.set

                is Entry.Collection -> for (division in entry.divisions.asSequence().flatMap { it.walk() }) {
                    for (set in division.items) byId[set.setId] = set
                }
            }
        }
    }
    return byId
}

/**
 * Newest first, on a copy — the shelf keeps the order it was built in.
 * Internal rather than private: [showsDepartmentOf] (`Departments.kt`) ranks
 * a department's own "New episodes" row the same way, over a department's
 * shows rather than a whole shelf.
 */
internal fun newestFirst(
    entries: List<Entry>,
    limit: Int,
): List<Entry> = entries.sortedByDescending(::arrivedAt).take(limit)

/**
 * When an entry last gained something.
 *
 * A collection is dated by its newest member, not its first: a series still
 * being uploaded keeps its place on the row, and one finished two years ago
 * does not hold the top of it for having been started recently.
 */
private fun arrivedAt(entry: Entry): Long =
    when (entry) {
        is Entry.Film -> {
            entry.set.addedAt
        }

        is Entry.Collection -> {
            entry.divisions
                .asSequence()
                .flatMap { it.walk() }
                .flatMap { it.items.asSequence() }
                .maxOfOrNull(MediaSet::addedAt)
                ?: 0
        }
    }
