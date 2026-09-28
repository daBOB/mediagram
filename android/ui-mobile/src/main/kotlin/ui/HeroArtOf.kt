package ui

import catalog.ANIME
import catalog.AnimeLibrary
import catalog.DOCUMENTARIES
import catalog.DocumentaryLibrary
import catalog.Entry
import catalog.Shelf
import catalog.animeDepartmentOf
import catalog.documentariesDepartmentOf
import catalog.firstItemOf
import catalog.moviesDepartmentOf
import catalog.showsDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import ui.catalog.filmsOf

/**
 * The backdrop the department at [title] would lead its own hero with right
 * now — the same lead each department screen picks for itself
 * ([moviesDepartmentOf], [showsDepartmentOf], [documentariesDepartmentOf]),
 * recomputed here rather than read back from whichever one is on screen —
 * the same reason [LibraryBranches]'s own `hasCover` is recomputed rather
 * than threaded down and back up. `null` for a title that names no
 * department at all (a kept wall, Collections, a plain shelf) or one with
 * nothing to lead with.
 */
internal fun heroArtOf(
    title: String?,
    shelves: List<Shelf>,
    byId: Map<String, MediaSet>,
    watch: WatchSnapshot,
): String? {
    val shelf = shelves.firstOrNull { it.title == title } ?: return null
    return when (title) {
        "Movies" -> moviesDepartmentOf(filmsOf(shelf)) { id -> watch.watched.any { it.setId == id } }?.lead?.backdropPath
        "Series" -> showsDepartmentOf(Kind.EPISODE, shelf.entries.filterIsInstance<Entry.Collection>(), byId, watch)
            ?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
        "Tutorials" -> showsDepartmentOf(Kind.TUTORIAL, shelf.entries.filterIsInstance<Entry.Collection>(), byId, watch)
            ?.lead?.let { firstItemOf(it.divisions) }?.backdropPath
        ANIME -> animeDepartmentOf(
            AnimeLibrary(
                shows = shelf.entries.filterIsInstance<Entry.Collection>(),
                films = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
            ),
            byId,
            watch,
        )?.lead?.backdropPath
        DOCUMENTARIES -> documentariesDepartmentOf(
            DocumentaryLibrary(
                collections = shelf.entries.filterIsInstance<Entry.Collection>(),
                singles = shelf.entries.filterIsInstance<Entry.Film>().map { it.set },
            ),
            byId,
            watch,
        )?.lead?.backdropPath
        else -> null
    }
}
