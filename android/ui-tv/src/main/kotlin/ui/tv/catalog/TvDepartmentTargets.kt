package ui.tv.catalog

import catalog.Entry
import catalog.MoviesDepartment
import catalog.ShowsDepartment
import catalog.keyOf
import ui.tv.TvMoviesPageEntryKey

/**
 * Where [TvMoviesDepartmentPage] sends the remote — a row name and a stop
 * along it — on arrival, or restoring [restoreKey]: a film on Featured,
 * Acclaimed or Recently added, a genre tile, [TvMoviesPageEntryKey] naming
 * the "All N films" link itself (the key opening the full `MOVIES_PAGE` wall
 * is recorded under, so Back from it lands back on the link rather than the
 * page's own first row), or — nothing named, or named but not found here —
 * the hero's Watch now when [heroFocusable], as the Series page and Home
 * arrive (a row of tall plates focused first would push the hero up under
 * the masthead), else the first non-empty row in the page's reading order.
 */
internal fun moviesDeptTargetOf(
    dept: MoviesDepartment,
    restoreKey: String?,
    heroFocusable: Boolean = false,
): Pair<String, Int> {
    if (restoreKey == TvMoviesPageEntryKey) return "all" to 0
    if (restoreKey != null) {
        dept.featured.indexOfFirst { it.setId == restoreKey }.takeIf { it >= 0 }?.let { return "featured" to it }
        dept.genres.indexOfFirst { it.name == restoreKey }.takeIf { it >= 0 }?.let { return "genres" to it }
        dept.acclaimed.indexOfFirst { it.setId == restoreKey }.takeIf { it >= 0 }?.let { return "acclaimed" to it }
        dept.recentlyAdded.indexOfFirst { it.setId == restoreKey }.takeIf { it >= 0 }?.let { return "recentlyAdded" to it }
    }
    return when {
        heroFocusable -> "hero" to 0
        dept.featured.isNotEmpty() -> "featured" to 0
        dept.genres.isNotEmpty() -> "genres" to 0
        dept.acclaimed.isNotEmpty() -> "acclaimed" to 0
        dept.recentlyAdded.isNotEmpty() -> "recentlyAdded" to 0
        else -> "all" to 0
    }
}

/**
 * Where [TvShowsDepartmentPage] sends the remote: the hero (when
 * [heroFocusable], its own Watch now), a header row's own stop, or `null` to
 * leave [TvWall]'s own restore-key/first-plate default alone — [restoreKey]
 * naming a show further down the wall itself, which that default already
 * finds, rather than one of the header rows above it.
 */
internal fun showsDeptTargetOf(
    dept: ShowsDepartment,
    underway: List<Entry>,
    heroFocusable: Boolean,
    restoreKey: String?,
): Pair<String, Int>? {
    if (restoreKey != null) {
        underway.indexOfFirst { keyOf(it) == restoreKey }.takeIf { it >= 0 }?.let { return "underway" to it }
        dept.popular.indexOfFirst { keyOf(it) == restoreKey }.takeIf { it >= 0 }?.let { return "popular" to it }
        dept.newEpisodes.indexOfFirst { keyOf(it) == restoreKey }.takeIf { it >= 0 }?.let { return "newEpisodes" to it }
        return null
    }
    return when {
        heroFocusable -> "hero" to 0
        underway.isNotEmpty() -> "underway" to 0
        dept.popular.isNotEmpty() -> "popular" to 0
        dept.newEpisodes.isNotEmpty() -> "newEpisodes" to 0
        else -> null
    }
}
