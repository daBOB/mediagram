package ui.tv.catalog

import catalog.Entry
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchGroups
import catalog.SearchRow
import catalog.VisiblePerson

/** One row of a grouped search result, whatever kind it names — the flat item [TvSearchResults] asks the remote to a stop on. */
internal sealed interface SearchEntry {
    data class Title(val row: SearchRow) : SearchEntry

    data class Show(val entry: Entry.Collection) : SearchEntry

    data class Person(val person: VisiblePerson) : SearchEntry

    data class Destination(val destination: SearchDestination) : SearchEntry
}

/** A stable key per [SearchEntry] — a set's own id for a title, and a prefixed one for everything else, so none collides with a real set id. */
internal fun keyOf(entry: SearchEntry): String =
    when (entry) {
        is SearchEntry.Title -> entry.row.set.setId
        is SearchEntry.Show -> "show:${entry.entry.key}"
        is SearchEntry.Person -> "person:${entry.person.personId}"
        is SearchEntry.Destination -> "dest:${entry.destination.href}"
    }

/** One named group of [SearchEntry] on the results page — empty ones are never returned, so a caller never has to check. */
internal data class SearchSection(val title: String, val entries: List<SearchEntry>)

/** The label a filter chip shows — [SearchFilter]'s own name, in the web's words. */
internal fun labelFor(filter: SearchFilter): String =
    when (filter) {
        SearchFilter.ALL -> "All"
        SearchFilter.MOVIES -> "Movies"
        SearchFilter.SERIES -> "Series"
        SearchFilter.TUTORIALS -> "Tutorials"
        SearchFilter.PEOPLE -> "People"
        SearchFilter.COLLECTIONS -> "Collections"
    }

/**
 * [groups] as the sections [filter] asks to see, in the web's own order —
 * films, matched shows, episodes, lessons, people, collections — with
 * [SearchFilter.ALL] showing every one that has something in it and every
 * other filter narrowing to its own single kind (also only when it has
 * something — a filter with a count of zero is never offered as a chip in
 * the first place, per [SearchGroups.filters]).
 */
internal fun sectionsFor(groups: SearchGroups, filter: SearchFilter): List<SearchSection> {
    fun wants(kind: SearchFilter) = filter == SearchFilter.ALL || filter == kind
    return buildList {
        if (wants(SearchFilter.MOVIES) && groups.films.isNotEmpty()) {
            add(SearchSection("Films", groups.films.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.SERIES) && groups.matchedShows.isNotEmpty()) {
            add(SearchSection("Series", groups.matchedShows.map(SearchEntry::Show)))
        }
        if (wants(SearchFilter.SERIES) && groups.episodes.isNotEmpty()) {
            add(SearchSection("Episodes", groups.episodes.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.TUTORIALS) && groups.lessons.isNotEmpty()) {
            add(SearchSection("Tutorials", groups.lessons.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.PEOPLE) && groups.people.isNotEmpty()) {
            add(SearchSection("People", groups.people.map(SearchEntry::Person)))
        }
        if (wants(SearchFilter.COLLECTIONS) && groups.collections.isNotEmpty()) {
            add(SearchSection("Collections", groups.collections.map(SearchEntry::Destination)))
        }
    }
}
