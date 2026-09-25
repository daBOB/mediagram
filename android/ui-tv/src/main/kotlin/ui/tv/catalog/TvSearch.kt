package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.CatalogUiState
import catalog.SearchUiState
import catalog.SearchViewModel
import catalog.searchRowsOf
import designsystem.Overscan
import model.WatchSnapshot
import ui.catalog.searchVisitState
import ui.tv.TvTextField

/** The search field's own tag — it carries no text of its own to be found by until something is typed. */
internal const val TvSearchFieldTag = "tv-search-field"

/**
 * Wires [SearchViewModel] to [TvSearchScreen], as the phone's `SearchBranch`
 * wires it to its own — when an answer belongs to this visit is
 * [searchVisitState]'s, the one rule both surfaces keep.
 */
@Composable
internal fun TvSearch(
    query: String,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restoreKey: String?,
    onQueryChange: (String) -> Unit,
    onPlay: (setId: String) -> Unit,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    TvSearchScreen(
        query = query,
        state = searchVisitState(viewModel, query),
        catalogState = catalogState,
        watch = watch,
        restoreKey = restoreKey,
        onQueryChange = { text ->
            onQueryChange(text)
            viewModel.setQuery(text)
        },
        onPlay = onPlay,
    )
}

/**
 * Search on a television — the phone's `SearchScreen` and the web's
 * `search-view.js`: a field, typed through the system keyboard, over one
 * flat ranked list of rows, where the best answer is first. Rows rather
 * than plates, as the phone and the web draw them: a hundred lessons named
 * "Definition" are told apart by where they sit and why they matched, which
 * a poster cannot say.
 *
 * The remote lands in the field when nothing has been typed, so the
 * keyboard is up the moment search opens. Coming back with a query —
 * from the title a row played — it waits for the answer and lands on that
 * row ([restoreKey]), or the first, rather than reopening the keyboard over
 * an answer already found. The keyboard's Search key hands the remote to
 * the first row; Up from the top row goes back to the field.
 *
 * [query] seeds the field from the saved position; every keystroke after
 * that is [onQueryChange]'s to save.
 */
@Composable
internal fun TvSearchScreen(
    query: String,
    state: SearchUiState,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    restoreKey: String?,
    onQueryChange: (String) -> Unit,
    onPlay: (setId: String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(query) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val catalogReady = catalogState is CatalogUiState.Ready
    val rows =
        remember(state, catalogState) {
            if (state is SearchUiState.Ready && catalogReady) searchRowsOf(state.hits, catalogState) else emptyList()
        }
    var ask by remember { mutableStateOf<RowAsk?>(null) }
    // Whether this visit has put the remote somewhere yet — at once for an
    // empty field, and once the answer is in when it arrives with a query.
    var arrived by remember { mutableStateOf(query.isBlank()) }
    val answered = catalogReady && (state is SearchUiState.Ready || state is SearchUiState.Failed)

    LaunchedEffect(Unit) { if (query.isBlank()) field.requestFocus() }
    LaunchedEffect(answered) {
        if (answered && !arrived) {
            arrived = true
            if (rows.isEmpty()) {
                field.requestFocus()
            } else {
                ask = RowAsk(rows.indexOfFirst { it.set.setId == restoreKey }.coerceAtLeast(0))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = Overscan.horizontal, end = Overscan.horizontal, top = Overscan.vertical)) {
        TvTextField(
            value = text,
            onValue = { new ->
                text = new
                onQueryChange(new)
            },
            onAction = {
                keyboard?.hide()
                if (rows.isNotEmpty()) ask = RowAsk(0)
            },
            focusRequester = field,
            fieldModifier = Modifier.testTag(TvSearchFieldTag),
            placeholder = "Search titles and summaries",
            imeAction = ImeAction.Search,
        )
        TvSearchResults(state, rows, catalogReady, watch, ask, onPlay)
    }
}

/**
 * One request for a row to take the remote. A fresh object each time, so
 * asking for the same row twice — Search pressed again on the same answer —
 * is still a new request.
 */
internal class RowAsk(val index: Int)
