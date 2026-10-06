package ui.tv.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchUiState
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
 * [SearchFilter] is chosen, each its own heading over its own entries, laid
 * out the way the web lays that kind out ([SearchLayout]): films and shows
 * as poster lines, people as lines of round portraits, collections as card
 * lines, everything else one row each.
 * [filters] is the chip row above them, offered only once there is more than
 * one kind to choose between (`SearchGroups.filters`'s own gate). [ask] is
 * the flat entry (across every section, in the same order they render) the
 * screen wants the remote on — its line scrolled to first, since an entry
 * below the fold has nothing to focus until it is laid out — and
 * [onAnswered] tells the screen it is done, so the request is never answered
 * twice.
 *
 * Down from the field or the chips above enters the results at their first
 * entry, the one the keyboard's Search key hands the remote to as well. Left
 * to geometry, Down from the full-width field would land on whichever poster
 * sits under its middle.
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
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenPerson: (personId: Long) -> Unit,
    onOpenDestination: (SearchDestination) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
) {
    if (state is SearchUiState.Failed) {
        Said(state.message)
        return
    }
    if (state !is SearchUiState.Ready) return
    val entries = remember(sections) { sections.flatMap { it.entries } }
    when (searchResultsView(catalogReady, entries.isEmpty())) {
        SearchResultsView.LOADING -> Said("Loading your library…")
        SearchResultsView.EMPTY -> Said("No title, folder or summary in the library mentions that.")
        SearchResultsView.ROWS -> {
            val marks = rememberWatchMarks(watch)
            val lines = remember(sections) { searchLinesOf(sections) }
            val listState = rememberLazyListState()
            // The first entry always carries [first], so Down from above can
            // always find it; [focus] is whichever other entry was asked for.
            val first = remember { FocusRequester() }
            val focus = remember { FocusRequester() }
            LaunchedEffect(ask) {
                val index = ask?.index ?: return@LaunchedEffect
                if (index in entries.indices) {
                    // Laid out by the time this returns: a jump remeasures the list at once.
                    listState.scrollToItem(lineOf(lines, index))
                    (if (index == 0) first else focus).requestFocus()
                }
                onAnswered()
            }
            Said(countOf(entries.size, "result"))
            if (filters.size >= 2) TvSearchFilterChips(filters, filter, onFilterChange)
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusProperties {
                            onEnter = {
                                val firstLine = lineOf(lines, 0)
                                val shown = listState.layoutInfo.visibleItemsInfo.any { it.index == firstLine }
                                if (requestedFocusDirection == FocusDirection.Down && shown) first.requestFocus()
                            }
                        }.focusGroup(),
                contentPadding = PaddingValues(bottom = Overscan.vertical),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                items(
                    items = lines,
                    key = { line ->
                        when (line) {
                            is SearchLine.Heading -> "heading-${line.title}"
                            is SearchLine.Entries -> "line-${keyOf(entries[line.indices.first])}"
                        }
                    },
                    contentType = { line -> if (line is SearchLine.Entries) line.layout else SearchLine.Heading::class },
                ) { line ->
                    when (line) {
                        is SearchLine.Heading -> TvSectionHeading(line.title, modifier = Modifier.padding(top = Spacing.small))
                        is SearchLine.Entries ->
                            SearchEntryLine(line) { at, modifier ->
                                val entry = entries[at]
                                // Keyed by the entry, not its slot, so a new answer never
                                // leaves the remote on whichever entry moved into it.
                                key(keyOf(entry)) {
                                    // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
                                    val own = remember { FocusRequester() }
                                    TvSearchCell(
                                        entry = entry,
                                        layout = line.layout,
                                        requester =
                                            when (at) {
                                                0 -> first
                                                ask?.index -> focus
                                                else -> own
                                            },
                                        marks = marks,
                                        modifier = modifier,
                                        onPlay = onPlay,
                                        onOpenTitle = onOpenTitle,
                                        onOpenCollection = onOpenCollection,
                                        onOpenPerson = onOpenPerson,
                                        onOpenDestination = onOpenDestination,
                                        shouldRequestPortrait = shouldRequestPortrait,
                                        fetchPortrait = fetchPortrait,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

/**
 * One line of entries: a row on its own across the width, or up to its
 * layout's [SearchLayout.columns] posters or cards side by side — a short
 * last line keeps the others' widths, as the web's grid tracks do.
 */
@Composable
private fun SearchEntryLine(
    line: SearchLine.Entries,
    cell: @Composable (at: Int, modifier: Modifier) -> Unit,
) {
    if (line.layout == SearchLayout.ROWS) {
        cell(line.indices.first, Modifier)
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for (at in line.indices) cell(at, Modifier.weight(1f))
        repeat(line.layout.columns - line.indices.count()) { Spacer(Modifier.weight(1f)) }
    }
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
