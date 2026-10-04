package ui.tv.catalog

import catalog.Entry
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchGroups
import catalog.SearchRow
import catalog.VisiblePerson

/** One entry of a grouped search result, whatever kind it names — the flat item [TvSearchResults] asks the remote to a stop on. */
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

/**
 * How a section lays its entries out, as `search-view.js` draws each part:
 * films and shows as posters, [Columns] across as on every wall; people as
 * the web's `personCard`s — a round portrait over the name — the same
 * [Columns] across; franchises and lists as the 4:3 destination cards
 * Collections draws, three across; and episodes, documentaries and lessons
 * one row each — a lesson's title alone rarely tells one from the next, so
 * its row says where it sits and why it matched.
 */
internal enum class SearchLayout(val columns: Int) { ROWS(1), POSTERS(Columns), PEOPLE(Columns), CARDS(DestinationColumns) }

/** One named group of [SearchEntry] on the results page — empty ones are never returned, so a caller never has to check. */
internal data class SearchSection(val title: String, val entries: List<SearchEntry>, val layout: SearchLayout = SearchLayout.ROWS)

/**
 * One item of the results list: a section's heading, or one line of its
 * entries — up to its layout's [SearchLayout.columns] side by side — named
 * by their flat indices, the ones [RowAsk.index] counts.
 */
internal sealed interface SearchLine {
    data class Heading(val title: String) : SearchLine

    data class Entries(val layout: SearchLayout, val indices: IntRange) : SearchLine
}

/**
 * [sections] as the results list lays them out, each heading followed by its
 * entries a line at a time. The indices are the entries' real, fixed
 * positions across every section — not a counter kept while composing: a
 * lazy item composes again whenever it scrolls into view, in whatever order
 * that happens to be.
 */
internal fun searchLinesOf(sections: List<SearchSection>): List<SearchLine> =
    buildList {
        var start = 0
        for (section in sections) {
            add(SearchLine.Heading(section.title))
            for (line in section.entries.indices.chunked(section.layout.columns)) {
                add(SearchLine.Entries(section.layout, start + line.first()..start + line.last()))
            }
            start += section.entries.size
        }
    }

/** Which of [lines] holds the flat entry [index] — `-1` for none. */
internal fun lineOf(
    lines: List<SearchLine>,
    index: Int,
): Int = lines.indexOfFirst { it is SearchLine.Entries && index in it.indices }

/** The label a filter chip shows — [SearchFilter]'s own name, in the web's words. */
internal fun labelFor(filter: SearchFilter): String =
    when (filter) {
        SearchFilter.ALL -> "All"
        SearchFilter.MOVIES -> "Movies"
        SearchFilter.SERIES -> "Series"
        SearchFilter.ANIME -> "Anime"
        SearchFilter.DOCUMENTARIES -> "Documentaries"
        SearchFilter.TUTORIALS -> "Tutorials"
        SearchFilter.PEOPLE -> "People"
        SearchFilter.COLLECTIONS -> "Collections"
    }

/**
 * [groups] as the sections [filter] asks to see, in the web's own order,
 * under its own headings (`search-view.js`'s `part` calls: Movies, as its
 * chip says, and Lessons under the Tutorials chip) and in its own layout
 * for each ([SearchLayout]) — films, matched shows,
 * episodes, documentaries, lessons, people, collections — with
 * [SearchFilter.ALL] showing every one that has something in it and every
 * other filter narrowing to its own single kind (also only when it has
 * something — a filter with a count of zero is never offered as a chip in
 * the first place, per [SearchGroups.filters]).
 */
internal fun sectionsFor(groups: SearchGroups, filter: SearchFilter): List<SearchSection> {
    fun wants(kind: SearchFilter) = filter == SearchFilter.ALL || filter == kind
    return buildList {
        if (wants(SearchFilter.MOVIES) && groups.films.isNotEmpty()) {
            add(SearchSection("Movies", groups.films.map(SearchEntry::Title), SearchLayout.POSTERS))
        }
        if (wants(SearchFilter.SERIES) && groups.matchedShows.isNotEmpty()) {
            add(SearchSection("Series", groups.matchedShows.map(SearchEntry::Show), SearchLayout.POSTERS))
        }
        if (wants(SearchFilter.SERIES) && groups.episodes.isNotEmpty()) {
            add(SearchSection("Episodes", groups.episodes.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.ANIME) && groups.animeFilms.isNotEmpty()) {
            add(SearchSection("Anime films", groups.animeFilms.map(SearchEntry::Title), SearchLayout.POSTERS))
        }
        if (wants(SearchFilter.ANIME) && groups.matchedAnimeShows.isNotEmpty()) {
            add(SearchSection("Anime series", groups.matchedAnimeShows.map(SearchEntry::Show), SearchLayout.POSTERS))
        }
        if (wants(SearchFilter.ANIME) && groups.animeEpisodes.isNotEmpty()) {
            add(SearchSection("Anime episodes", groups.animeEpisodes.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.DOCUMENTARIES) && groups.documentaries.isNotEmpty()) {
            add(SearchSection("Documentaries", groups.documentaries.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.TUTORIALS) && groups.lessons.isNotEmpty()) {
            add(SearchSection("Lessons", groups.lessons.map(SearchEntry::Title)))
        }
        if (wants(SearchFilter.PEOPLE) && groups.people.isNotEmpty()) {
            add(SearchSection("People", groups.people.map(SearchEntry::Person), SearchLayout.PEOPLE))
        }
        if (wants(SearchFilter.COLLECTIONS) && groups.collections.isNotEmpty()) {
            add(SearchSection("Collections", groups.collections.map(SearchEntry::Destination), SearchLayout.CARDS))
        }
    }
}

/**
 * One request for an entry to take the remote. A fresh object each time, so
 * asking for the same entry twice — Search pressed again on the same answer —
 * is still a new request. Dropped the moment it is answered: the entries
 * leave composition whenever an answer has none, and a request still
 * standing when they came back would pull the remote out of the field
 * mid-typing.
 */
internal class RowAsk(val index: Int)
