package ui.tv.catalog

import catalog.Entry
import catalog.MoviesDepartment
import catalog.SetCard
import catalog.ShowsDepartment

/** One stop on a page: which section, and which of its own stops in order. */
internal data class SectionStop<S>(val section: S, val stop: Int) {
    /** [stop] when this target is in [section], else `null` — what a row reads as its own `focusAt`. */
    fun stopAt(section: S): Int? = stop.takeIf { this.section == section }
}

/**
 * One section of a page's own arrival/restore map: its stops' keys, in the
 * order they draw down (or across) the page. A section with no stops was
 * never drawn at all — the same "nothing to show" every row already skips.
 */
internal data class DeptSection<S>(val id: S, val stops: List<String>)

/** The rows of the Movies page that can hold the remote, in the order they draw. */
internal enum class MoviesSection { FEATURED, GENRES, ACCLAIMED, RECENTLY_ADDED, ALL }

// The Series/Tutorials, Anime and Documentaries pages grow a row per
// category or folder, so their sections are named by these strings rather
// than an enum; the targets below and the pages that draw the rows share them.
internal const val UnderwaySection = "underway"
internal const val PopularSection = "popular"
internal const val NewEpisodesSection = "newEpisodes"
internal const val ContinueSection = "continue"
internal const val RecentlyAddedSection = "recentlyAdded"
internal const val StandaloneSection = "standalone"

internal fun categorySection(index: Int) = "category:$index"

internal fun groupSection(index: Int) = "group:$index"

/**
 * Where a [restoreKey] lands among [sections], the one rule Home and every
 * department page share: a key in [lastSection] — the row the remote was
 * last in — wins while that row still carries it, else the first section
 * down the page that does. `null` [restoreKey], or one no section carries,
 * answers `null`, and the caller's own default decides.
 */
internal fun <S> restoreTargetOf(
    sections: List<DeptSection<S>>,
    restoreKey: String?,
    lastSection: S? = null,
): SectionStop<S>? {
    if (restoreKey == null) return null
    lastSection?.let { last ->
        val stop = sections.firstOrNull { it.id == last }?.stops?.indexOf(restoreKey) ?: -1
        if (stop >= 0) return SectionStop(last, stop)
    }
    for (section in sections) {
        val stop = section.stops.indexOf(restoreKey)
        if (stop >= 0) return SectionStop(section.id, stop)
    }
    return null
}

/** The first section with any stops, at its first one; `null` when every section is empty. */
internal fun <S> firstStopOf(sections: List<DeptSection<S>>): SectionStop<S>? = sections.firstOrNull { it.stops.isNotEmpty() }?.let { SectionStop(it.id, 0) }

/** The Movies page's rows that carry stops of their own, in page order; the "All N films" link is not one of them. */
internal fun moviesSections(dept: MoviesDepartment): List<DeptSection<MoviesSection>> =
    listOf(
        DeptSection(MoviesSection.FEATURED, dept.featured.map { it.setId }),
        DeptSection(MoviesSection.GENRES, dept.genres.map { it.name }),
        DeptSection(MoviesSection.ACCLAIMED, dept.acclaimed.map { it.setId }),
        DeptSection(MoviesSection.RECENTLY_ADDED, dept.recentlyAdded.map { it.setId }),
    )

/**
 * Where [TvMoviesDepartmentPage] sends the remote — a row and a stop along
 * it — on arrival, or restoring [restoreKey]: a film on Featured, Acclaimed
 * or Recently added, a genre tile, [TvMoviesPageEntryKey] naming the "All N
 * films" link itself (the key opening the full `MOVIES_PAGE` wall is
 * recorded under, so Back from it lands back on the link rather than the
 * page's own first row), or — nothing named, or named but not found here —
 * the first non-empty row in the page's reading order. The hero is never a
 * candidate: it carries no focusable stop of its own.
 */
internal fun moviesDeptTargetOf(
    dept: MoviesDepartment,
    restoreKey: String?,
    lastSection: MoviesSection? = null,
): SectionStop<MoviesSection> {
    if (restoreKey == TvMoviesPageEntryKey) return SectionStop(MoviesSection.ALL, 0)
    val sections = moviesSections(dept)
    return restoreTargetOf(sections, restoreKey, lastSection) ?: firstStopOf(sections) ?: SectionStop(MoviesSection.ALL, 0)
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
): SectionStop<String>? {
    val sections = showsSections(dept, underway)
    if (restoreKey != null) return restoreTargetOf(sections, restoreKey, lastSection)
    return firstStopOf(sections)
}

private fun showsSections(
    dept: ShowsDepartment,
    underway: List<SetCard>,
): List<DeptSection<String>> =
    buildList {
        add(DeptSection(UnderwaySection, underway.map { it.set.setId }))
        dept.categories.forEachIndexed { i, row -> add(DeptSection(categorySection(i), row.units.map(Entry.Collection::key))) }
        add(DeptSection(PopularSection, dept.popular.map(Entry.Collection::key)))
        add(DeptSection(NewEpisodesSection, dept.newEpisodes.map(Entry.Collection::key)))
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
): SectionStop<String>? {
    val sections = listOf(DeptSection(ContinueSection, resumeCards.map { it.set.setId }))
    if (restoreKey != null) return restoreTargetOf(sections, restoreKey, lastSection)
    return firstStopOf(sections)
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
    sections: List<DeptSection<String>>,
    restoreKey: String?,
    lastSection: String? = null,
): SectionStop<String> = restoreTargetOf(sections, restoreKey, lastSection) ?: firstStopOf(sections) ?: SectionStop(ContinueSection, 0)
