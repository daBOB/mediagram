package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Entry
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchRow
import catalog.SearchUiState
import catalog.VisiblePerson
import catalog.extentOf
import catalog.initialsOf
import catalog.searchWhy
import catalog.watchedFractionOf
import coil3.compose.AsyncImage
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.WatchSnapshot
import ui.catalog.SearchResultsView
import ui.catalog.countOf
import ui.catalog.isPlayable
import ui.catalog.locationOf
import ui.catalog.rememberPortrait
import ui.catalog.searchMetaLineOf
import ui.catalog.searchResultsView
import ui.tv.TvFocus
import ui.tv.TvTextRow

/** How wide a person's own portrait sits on their search row. */
private val SearchPortraitWidth = 56.dp

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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Overscan.vertical),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                var index = 0
                for (section in sections) {
                    item(key = "heading-${section.title}") { TvSectionHeading(section.title, modifier = Modifier.padding(top = Spacing.small)) }
                    itemsIndexed(section.entries, key = { _, entry -> keyOf(entry) }) { _, entry ->
                        val at = index++
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

/**
 * One hit, as the phone's `SearchResultRow` draws it: the title, where it
 * sits, the summary line that matched, why it matched, what the file is,
 * and this viewer's progress. A press plays it straight away, as a tap does
 * on the phone — a search hit is a set, not a way to somewhere else.
 *
 * A document is shown and not opened, and reads as disabled, the rule a
 * course's own rows keep; the remote can still rest on it. Why it matched
 * is set apart by italics rather than the phone's accent colour: on a
 * television that red means only where the remote is.
 *
 * A title this device holds carries the phone's "offline" badge, from the
 * same [SearchRow.held] the phone reads.
 */
@Composable
private fun TvSearchRow(
    row: SearchRow,
    progress: Float?,
    watched: Boolean,
    onPlay: (setId: String) -> Unit,
    focus: FocusRequester?,
) {
    val set = row.set
    val playable = isPlayable(set)
    // Read from the focus state itself, as TvTextRow does: a row focused on
    // arrival never hears a focus interaction.
    var focused by remember { mutableStateOf(false) }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focus != null) it.focusRequester(focus) else it }
                .onFocusChanged { focused = it.isFocused }
                .let {
                    if (playable) {
                        it.clickable(indication = null, interactionSource = null) { onPlay(set.setId) }
                    } else {
                        it.focusable().semantics(mergeDescendants = true) { disabled() }
                    }
                },
    ) {
        val base = if (playable) TvTypeScale.body else TvTypeScale.body.copy(color = quiet)
        Text(text = "${if (watched) "✓ " else ""}${set.title}", style = TvFocus.textStyle(base, focused))
        if (!playable) TvQuietLine(DocumentReason)
        locationOf(set)?.let { TvQuietLine(it) }
        // The reason a summary hit is worth showing at all.
        row.excerpt?.let { TvQuietLine(it) }
        searchWhy(row.matched)?.let { Text(text = it, style = TvTypeScale.body, fontStyle = FontStyle.Italic, color = quiet) }
        searchMetaLineOf(set).takeIf(String::isNotEmpty)?.let { TvQuietLine(it) }
        TvItemMarks(progress, row.held)
    }
}

/** A matched show, rolled up from its episodes rather than listed once per one — [catalog.searchGroupsOf]'s own rule. */
@Composable
private fun TvShowSearchRow(
    entry: Entry.Collection,
    onOpenCollection: (key: String) -> Unit,
    focus: FocusRequester?,
) {
    TvTextRow(
        text = "${entry.name} · ${extentOf(entry)}",
        onClick = { onOpenCollection(entry.key) },
        modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
    )
}

/**
 * A person the query matched, already narrowed to titles *this profile* can
 * see ([catalog.visiblePeople]'s own rule — never shown otherwise). A round
 * portrait, fetched lazily and at most once per session, the same rule
 * every cast row on this surface follows.
 */
@Composable
private fun TvPersonSearchRow(
    person: VisiblePerson,
    onOpenPerson: (personId: Long) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
    focus: FocusRequester?,
) {
    val portrait = rememberPortrait(person.personId, person.portraitPath, shouldRequestPortrait, fetchPortrait)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focus != null) it.focusRequester(focus) else it }
                .onFocusChanged { focused = it.isFocused }
                // Merged, the same reason `TvPlate`'s own Card is: the name
                // and title count are this row's one announcement, and a
                // press anywhere on it is the same one press.
                .semantics(mergeDescendants = true) {}
                .clickable(indication = null, interactionSource = null) { onOpenPerson(person.personId) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPortraitCircle(portrait?.let(::File), person.name, modifier = Modifier.width(SearchPortraitWidth))
        Column {
            Text(text = person.name, style = TvFocus.textStyle(TvTypeScale.body, focused))
            TvQuietLine("${person.titles} ${if (person.titles == 1) "title" else "titles"}")
        }
    }
}

/**
 * A person's portrait, round rather than a poster's rectangle — the shape
 * search's own people rows use, matching the web's own people avatars.
 * [initialsOf] stands in for a missing portrait, the same fallback every
 * plate on this surface draws.
 */
@Composable
private fun TvPortraitCircle(
    path: File?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = path, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = initialsOf(name), style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A franchise or the viewer's own list, matched by name — [catalog.SearchDestination]'s own two sources. */
@Composable
private fun TvDestinationSearchRow(
    destination: SearchDestination,
    onOpenDestination: (SearchDestination) -> Unit,
    focus: FocusRequester?,
) {
    TvTextRow(
        text = "${destination.name} · ${destination.itemCount} ${if (destination.itemCount == 1) "title" else "titles"}",
        onClick = { onOpenDestination(destination) },
        modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
    )
}
