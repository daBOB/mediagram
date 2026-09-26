package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchUiState
import catalog.watchedFractionOf
import designsystem.Overscan
import designsystem.Spacing
import model.WatchSnapshot
import ui.catalog.SearchResultsView
import ui.catalog.countOf
import ui.catalog.searchResultsView
import ui.tv.TvTextRow

/**
 * What the search screen says under its field, in the phone's words: nothing
 * before anything is typed, the count and the sections once there is an
 * answer, and the phone's sentences for a failure, a library still loading
 * and an answer with nothing in it.
 *
 * [sections] is [groups][catalog.SearchGroups] already narrowed to whichever
 * [SearchFilter] is chosen — films, matched shows, episodes, lessons, people
 * and collections, in that order, each its own heading over its own rows;
 * [filters] is the chip row above them, offered only once there is more than
 * one kind to choose between (`SearchGroups.filters`'s own gate). [ask] is
 * the flat entry (across every section, in the same order they render) the
 * screen wants the remote on — scrolled to first, since a row below the fold
 * has nothing to focus until it is laid out — and [onAnswered] tells the
 * screen it is done, so the request is never answered twice.
 */
@Composable
internal fun TvSearchResults(
    state: SearchUiState,
    sections: List<SearchSection>,
    filters: List<Pair<SearchFilter, Int>>,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    catalogReady: Boolean,
    watch: WatchSnapshot,
    ask: RowAsk?,
    onAnswered: () -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenPerson: (personId: Long) -> Unit,
    onOpenDestination: (SearchDestination) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
) {
    if (state is SearchUiState.Failed) {
        Said("Search failed: ${state.message}")
        return
    }
    if (state !is SearchUiState.Ready) return
    val entries = remember(sections) { sections.flatMap { it.entries } }
    when (searchResultsView(catalogReady, entries.isEmpty())) {
        SearchResultsView.LOADING -> Said("Loading your library…")
        SearchResultsView.EMPTY -> Said("No title, folder or summary in the library mentions that.")
        SearchResultsView.ROWS -> {
            val (positions, watchedIds) = rememberWatchMarks(watch)
            val listState = rememberLazyListState()
            val focus = remember { FocusRequester() }
            LaunchedEffect(ask) {
                val index = ask?.index ?: return@LaunchedEffect
                if (index in entries.indices) {
                    listState.scrollToItem(displayIndexOf(sections, index))
                    focus.requestFocus()
                }
                onAnswered()
            }
            Said(countOf(entries.size, "result"))
            if (filters.size >= 2) TvSearchFilterChips(filters, filter, onFilterChange)
            val starts = remember(sections) { sectionStartsOf(sections) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Overscan.vertical),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                for ((sectionIndex, section) in sections.withIndex()) {
                    item(key = "heading-${section.title}") { TvSectionHeading(section.title, modifier = Modifier.padding(top = Spacing.small)) }
                    val start = starts[sectionIndex]
                    itemsIndexed(section.entries, key = { _, entry -> keyOf(entry) }) { localIndex, entry ->
                        // The entry's real, fixed position — [start] plus its
                        // own place in this section — not a shared counter:
                        // a lazy item's content composes again whenever it
                        // scrolls into view or something it reads changes,
                        // in whatever order that happens to be, never
                        // guaranteed to be section order.
                        val at = start + localIndex
                        val itemFocus = focus.takeIf { at == ask?.index }
                        when (entry) {
                            is SearchEntry.Title ->
                                TvSearchRow(
                                    row = entry.row,
                                    progress = watchedFractionOf(positions[entry.row.set.setId]),
                                    watched = entry.row.set.setId in watchedIds,
                                    onPlay = onPlay,
                                    focus = itemFocus,
                                )

                            is SearchEntry.Show -> TvShowSearchRow(entry.entry, onOpenCollection, itemFocus)

                            is SearchEntry.Person ->
                                TvPersonSearchRow(entry.person, onOpenPerson, shouldRequestPortrait, fetchPortrait, itemFocus)

                            is SearchEntry.Destination -> TvDestinationSearchRow(entry.destination, onOpenDestination, itemFocus)
                        }
                    }
                }
            }
        }
    }
}

/** Where each [SearchSection] starts in the flat, cross-section entry list [RowAsk.index] and [displayIndexOf] both count against. */
internal fun sectionStartsOf(sections: List<SearchSection>): List<Int> {
    var seen = 0
    return sections.map { section -> seen.also { seen += section.entries.size } }
}

/** The flat [entryIndex]'s own position once section headings are counted in, for [rememberLazyListState.scrollToItem]. */
private fun displayIndexOf(sections: List<SearchSection>, entryIndex: Int): Int {
    var seen = 0
    var display = 0
    for (section in sections) {
        display++ // the heading
        if (entryIndex < seen + section.entries.size) return display + (entryIndex - seen)
        seen += section.entries.size
        display += section.entries.size
    }
    return display
}

/** The filter chips over the results — pressed, not focused-into, the masthead's own reason: focus walking the row must not swap the list under it. */
@Composable
private fun TvSearchFilterChips(
    filters: List<Pair<SearchFilter, Int>>,
    selected: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        for ((kind, count) in filters) {
            val marker = if (kind == selected) "●" else "○"
            TvTextRow(text = "$marker  ${labelFor(kind)} · $count", onClick = { onSelect(kind) })
        }
    }
}

@Composable
private fun Said(text: String) {
    TvQuietLine(text, Modifier.padding(vertical = Spacing.medium))
}
