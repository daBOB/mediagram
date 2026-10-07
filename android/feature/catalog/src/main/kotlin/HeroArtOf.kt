package catalog

import model.Kind
import model.MediaSet
import model.WatchSnapshot

/**
 * The backdrop [department] would lead its own hero with right now — the
 * same lead each department screen picks for itself ([moviesDepartmentOf],
 * [showsDepartmentOf], [animeDepartmentOf], [documentariesDepartmentOf]),
 * recomputed here rather than read back from whichever one is on screen —
 * the same reason `ui.LibraryBranches`' own `hasCover` is recomputed rather
 * than threaded down and back up. `null` for no department at all (Home, a
 * kept wall, Collections), one not on [shelves], or one with nothing to lead
 * with.
 *
 * Public rather than internal, unlike its own first home: both the tablet's
 * `LibraryBranchSupport` and the television's `TvCatalogScreen` read this to
 * decide their own bar's over-hero blend, and the two live in separate
 * Gradle modules that only ever share this one through `feature:catalog`.
 */
fun heroArtOf(
    department: Department?,
    shelves: List<Shelf>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
): String? {
    val shelf = shelves.firstOrNull { it.department == department } ?: return null
    return when (shelf.department) {
        Department.MOVIES -> {
            val films = shelf.entries.filterIsInstance<Entry.Film>().map { it.set }
            moviesDepartmentOf(films) { id -> watch.watched.any { it.setId == id } }?.lead?.backdropPath
        }
        Department.SERIES -> showsDepartmentOf(Kind.EPISODE, shelf.entries.filterIsInstance<Entry.Collection>(), byId, watch)
            ?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
        Department.TUTORIALS -> showsDepartmentOf(Kind.TUTORIAL, shelf.entries.filterIsInstance<Entry.Collection>(), byId, watch)
            ?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
        Department.ANIME -> animeDepartmentOf(
            AnimeLibrary(
                shows = shelf.entries.filterIsInstance<Entry.Collection>(),
                films = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
            ),
            byId,
            watch,
        )?.lead?.backdropPath
        Department.DOCUMENTARIES -> documentariesDepartmentOf(
            DocumentaryLibrary(
                collections = shelf.entries.filterIsInstance<Entry.Collection>(),
                singles = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
            ),
            byId,
            watch,
        )?.lead?.backdropPath
    }
}
