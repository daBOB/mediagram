package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot

/** How many of each kind — films, shows, courses — the Latest page holds: the web's `LATEST_LIMIT` (`utility-pages.js`). */
internal const val LATEST_PAGE_LIMIT = 48

/**
 * Every page of the library a surface draws, decided once from the shelves,
 * the viewer's watch state, what this device holds and the time: Home, Latest,
 * the department front pages and the two kept walls. The television and the
 * phone render these and decide nothing of their own about what a page holds,
 * so the two cannot drift apart the way separate assemblies did. A department
 * whose shelf is empty or absent has no page (`null`).
 */
data class LibraryPages(
    val home: HomePage,
    val latest: Latest,
    val movies: MoviesDepartment?,
    val series: ShowsDepartment?,
    val anime: AnimeDepartment?,
    val documentaries: DocumentariesDepartment?,
    val tutorials: ShowsDepartment?,
    /** Continue's wall: every title with a position worth resuming, newest first. */
    val continueWall: List<MediaSet>,
    /** My List's wall, newest added first. */
    val watchlistWall: List<MediaSet>,
) {
    /**
     * The backdrop [department]'s own hero leads with — its page's own lead,
     * read rather than recomputed — for the bar's over-hero blend. `null` for
     * no department at all (Home, a kept wall, Collections), one without a
     * page, or one with nothing to lead with.
     */
    fun heroArtOf(department: Department?): String? =
        when (department) {
            Department.MOVIES -> movies?.lead?.backdropPath
            Department.SERIES -> series?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
            Department.TUTORIALS -> tutorials?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
            Department.ANIME -> anime?.lead?.backdropPath
            Department.DOCUMENTARIES -> documentaries?.lead?.backdropPath
            null -> null
        }

    companion object {
        /** The pages of no library — what a state not yet given its own carries. */
        val Empty = libraryPagesOf(emptyList(), WatchSnapshot.Empty, emptySet(), now = 0)
    }
}

/** Home: the magazine, and the Latest series and courses rows beneath it. */
data class HomePage(
    val magazine: MagazineHome,
    val latest: Latest,
)

/** [LibraryPages] for [shelves] as [watch] has seen them, [heldIds] on this device, at [now]. */
fun libraryPagesOf(
    shelves: List<Shelf>,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    now: Long,
): LibraryPages {
    val byId = allSetsById(shelves)
    val films = filmIndexOf(shelves).values
    val watchedIds = filmsWatched(watch.watched.mapTo(HashSet()) { it.setId }, films)
    // Every Continue row below sees a film held more than once once.
    val continuing = watch.continuing(shelves)

    fun entriesOf(department: Department) = shelves.firstOrNull { it.department == department }?.entries.orEmpty()
    fun collectionsOf(department: Department) = entriesOf(department).filterIsInstance<Entry.Collection>()
    fun filmsOf(department: Department) = entriesOf(department).filterIsInstance<Entry.Film>().map { it.set }

    return LibraryPages(
        home =
            HomePage(
                magazine =
                    magazineHomeOf(
                        shelves,
                        continuing,
                        editorsChoice = watch.editorsChoice,
                        now = now,
                        heldIds = heldIds,
                        recentLimit = HOME_POSTER_ROW_LIMIT,
                    ),
                latest = latestOf(shelves),
            ),
        latest = latestOf(shelves, posterLimit = LATEST_PAGE_LIMIT, limit = LATEST_PAGE_LIMIT),
        movies = moviesDepartmentOf(filmsOf(Department.MOVIES)) { it in watchedIds },
        series = showsDepartmentOf(Kind.EPISODE, collectionsOf(Department.SERIES), byId, continuing, heldIds),
        anime =
            animeDepartmentOf(
                AnimeLibrary(shows = collectionsOf(Department.ANIME), films = filmsOf(Department.ANIME)),
                byId,
                continuing,
                heldIds,
            ),
        documentaries =
            documentariesDepartmentOf(
                DocumentaryLibrary(collections = collectionsOf(Department.DOCUMENTARIES), singles = filmsOf(Department.DOCUMENTARIES)),
                byId,
                continuing,
                heldIds,
            ),
        tutorials = showsDepartmentOf(Kind.TUTORIAL, collectionsOf(Department.TUTORIALS), byId, continuing, heldIds),
        continueWall = continueWall(shelves, watch),
        watchlistWall = watchlistWall(shelves, watch),
    )
}

/** This state with the pages its own shelves, watch and held ids make at [now]. */
fun CatalogUiState.Ready.paged(now: Long = System.currentTimeMillis()): CatalogUiState.Ready = copy(pages = libraryPagesOf(shelves, watch, heldIds, now))
