package ui.tv.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.SearchRow
import catalog.SearchUiState
import catalog.searchWhy
import catalog.watchedFractionOf
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.WatchSnapshot
import ui.catalog.SearchResultsView
import ui.catalog.countOf
import ui.catalog.isPlayable
import ui.catalog.locationOf
import ui.catalog.searchMetaLineOf
import ui.catalog.searchResultsView
import ui.tv.TvFocus

/**
 * What the search screen says under its field, in the phone's words: nothing
 * before anything is typed, the count and the rows once there is an answer,
 * and the phone's sentences for a failure, a library still loading and an
 * answer with nothing in it. [ask] is the row the screen wants the remote
 * on — scrolled to first, since a row below the fold has nothing to focus
 * until it is laid out — and [onAnswered] tells the screen it is done, so
 * the request is never answered twice.
 */
@Composable
internal fun TvSearchResults(
    state: SearchUiState,
    rows: List<SearchRow>,
    catalogReady: Boolean,
    watch: WatchSnapshot,
    ask: RowAsk?,
    onAnswered: () -> Unit,
    onPlay: (setId: String) -> Unit,
) {
    if (state is SearchUiState.Failed) {
        Said("Search failed: ${state.message}")
        return
    }
    if (state !is SearchUiState.Ready) return
    when (searchResultsView(catalogReady, rows.isEmpty())) {
        SearchResultsView.LOADING -> Said("Loading your library…")
        SearchResultsView.EMPTY -> Said("No title, folder or summary in the library mentions that.")
        SearchResultsView.ROWS -> {
            val (positions, watchedIds) = rememberWatchMarks(watch)
            val listState = rememberLazyListState()
            val focus = remember { FocusRequester() }
            LaunchedEffect(ask) {
                val index = ask?.index ?: return@LaunchedEffect
                if (index in rows.indices) {
                    listState.scrollToItem(index)
                    focus.requestFocus()
                }
                onAnswered()
            }
            Said(countOf(rows.size, "result"))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Overscan.vertical),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                itemsIndexed(rows, key = { _, row -> row.set.setId }) { index, row ->
                    TvSearchRow(
                        row = row,
                        progress = watchedFractionOf(positions[row.set.setId]),
                        watched = row.set.setId in watchedIds,
                        onPlay = onPlay,
                        focus = focus.takeIf { index == ask?.index },
                    )
                }
            }
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
 * The phone's "offline" badge has no twin: the television keeps nothing on
 * the device for later.
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
        progress?.let { TvProgressRule(fraction = it, modifier = Modifier.padding(top = Spacing.small)) }
    }
}
