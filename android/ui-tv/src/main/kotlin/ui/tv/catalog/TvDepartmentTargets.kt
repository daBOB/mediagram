package ui.tv.catalog

import catalog.Entry
import catalog.MoviesDepartment
import catalog.SetCard
import catalog.ShowsDepartment
import ui.tv.TvMoviesPageEntryKey

/**
 * One named section of a department page's own arrival/restore map: its own
 * stops' keys, in the order they draw down (or across) the page. A section
 * with no stops was never drawn at all — the same "nothing to show" every
 * row already skips.
 */
internal data class DeptSection(val name: String, val stops: List<String>)

/**
 * Where a [restoreKey] found in some [sections] lands — [ui.tv.catalog.home.homeTargetOf]'s
 * own rule, generalised over a page's own named rows rather than one fixed
 * enum, so Home and every department page share the one search rather than
 * each keeping a copy of it. A key carried by two rows at once goes to
 * [lastSection] when that row still has it, else the first row down the page
 * that does; `null` [restoreKey], or one no row carries, answers `null` —
 * each page's own arrival default decides what happens then, since that
 * default differs between Movies (falls through to its own first row) and
 * Series/Tutorials/Anime/Documentaries (defers to the wall beneath their own
 * header rows instead).
 */
internal fun restoreTargetOf(
    sections: List<DeptSection>,
    restoreKey: String?,
    lastSection: String? = null,
): Pair<String, Int>? {
    if (restoreKey == null) return null
    lastSection?.let { last ->
        val stop = sections.firstOrNull { it.name == last }?.stops?.indexOf(restoreKey) ?: -1
        if (stop >= 0) return last to stop
    }
    for (section in sections) {
        val stop = section.stops.indexOf(restoreKey)
        if (stop >= 0) return section.name to stop
    }
    return null
}

/**
 * Where [TvMoviesDepartmentPage] sends the remote — a row name and a stop
 * along it — on arrival, or restoring [restoreKey]: a film on Featured,
 * Acclaimed or Recently added, a genre tile, [TvMoviesPageEntryKey] naming
 * the "All N films" link itself (the key opening the full `MOVIES_PAGE` wall
 * is recorded under, so Back from it lands back on the link rather than the
 * page's own first row), or — nothing named, or named but not found here —
 * the first non-empty row in the page's reading order. The hero is never a
 * candidate: it carries no focusable stop of its own any more.
 */
internal fun moviesDeptTargetOf(
    dept: MoviesDepartment,
    restoreKey: String?,
    lastSection: String? = null,
): Pair<String, Int> {
    if (restoreKey == TvMoviesPageEntryKey) return "all" to 0
    val sections =
        listOf(
            DeptSection("featured", dept.featured.map { it.setId }),
            DeptSection("genres", dept.genres.map { it.name }),
            DeptSection("acclaimed", dept.acclaimed.map { it.setId }),
            DeptSection("recentlyAdded", dept.recentlyAdded.map { it.setId }),
        )
    restoreTargetOf(sections, restoreKey, lastSection)?.let { return it }
    return sections.firstOrNull { it.stops.isNotEmpty() }?.let { it.name to 0 } ?: ("all" to 0)
}

/**
 * Where [TvShowsDepartmentPage] sends the remote: a header row's own stop —
 * Continue, then a category row (in [ShowsDepartment.categories]'s own
 * order), then Popular/New episodes — or `null` to leave [TvWall]'s own
 * restore-key/first-plate default alone — [restoreKey] naming a show further
 * down the wall itself, which that default already finds, rather than one of
 * the header rows above it.
 */
internal fun showsDeptTargetOf(
    dept: ShowsDepartment,
    underway: List<SetCard>,
    restoreKey: String?,
    lastSection: String? = null,
): Pair<String, Int>? {
    val sections = showsSections(dept, underway)
    if (restoreKey != null) return restoreTargetOf(sections, restoreKey, lastSection)
    return sections.firstOrNull { it.stops.isNotEmpty() }?.let { it.name to 0 }
}

private fun showsSections(
    dept: ShowsDepartment,
    underway: List<SetCard>,
): List<DeptSection> =
    buildList {
        add(DeptSection("underway", underway.map { it.set.setId }))
        dept.categories.forEachIndexed { i, row -> add(DeptSection("category:$i", row.units.map(Entry.Collection::key))) }
        add(DeptSection("popular", dept.popular.map(Entry.Collection::key)))
        add(DeptSection("newEpisodes", dept.newEpisodes.map(Entry.Collection::key)))
    }

/**
 * Where [TvAnimeDepartmentPage] sends the remote: Continue watching's own
 * stop, or `null` to leave the Series/Films wall beneath it to [TvWall]'s
 * own default — the same split [showsDeptTargetOf] makes between its header
 * rows and the wall under them.
 */
internal fun animeDeptTargetOf(
    resumeCards: List<SetCard>,
    restoreKey: String?,
    lastSection: String? = null,
): Pair<String, Int>? {
    val sections = listOf(DeptSection("continue", resumeCards.map { it.set.setId }))
    if (restoreKey != null) return restoreTargetOf(sections, restoreKey, lastSection)
    return sections.firstOrNull { it.stops.isNotEmpty() }?.let { it.name to 0 }
}

/**
 * Where [TvDocumentariesDepartmentPage] sends the remote — Continue
 * watching, then a category row (in [catalog.DocumentariesDepartment.categories]'s
 * own order) — the same header-rows-then-wall split [showsDeptTargetOf]
 * makes, except every row past the header ones ([catalog.DocumentariesDepartment.recentlyAdded],
 * one per folder, standalone) is this page's own [ui.tv.catalog.TvDepartmentRows]
 * strip rather than a wall, so a restore key naming a title in any of those
 * still has to be searched here too, not left to a wall that does not exist
 * on this page.
 */
internal fun documentariesDeptTargetOf(
    sections: List<DeptSection>,
    restoreKey: String?,
    lastSection: String? = null,
): Pair<String, Int> {
    restoreTargetOf(sections, restoreKey, lastSection)?.let { return it }
    return sections.firstOrNull { it.stops.isNotEmpty() }?.let { it.name to 0 } ?: ("continue" to 0)
}
