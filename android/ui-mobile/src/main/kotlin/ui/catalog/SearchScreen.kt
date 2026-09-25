package ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.CatalogUiState
import catalog.SearchUiState
import catalog.SearchViewModel
import catalog.searchRowsOf
import designsystem.Spacing
import model.WatchSnapshot

/**
 * A hundred and seventy lessons named "Definition" are not browsable, only
 * searchable — the web's `search-view.js` ported to a field a viewer opens
 * on purpose rather than one always on screen, which is the touch surface's
 * equivalent of the web's own header box.
 *
 * [query] seeds the field from the position that survives a rotation or a
 * killed process; every keystroke after that is [onQueryChange]'s to save.
 * Left only by the bar's own back arrow or the system gesture, both of
 * which [LibraryBranch] already wires to the same departure — clearing the
 * field shows the empty state, the same as opening it fresh, rather than
 * leaving on the keystroke itself. The web moves the address bar back once
 * its pause settles on nothing typed, but a touch keyboard has no such
 * pause to wait out, and a screen that can vanish under a still-held
 * backspace is worse than one a viewer always has to ask to leave.
 */
@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    onQueryChange: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(query) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Focused and shown once, on the way in, and only when there is nothing
    // typed yet: a viewer back from playing a hit is looking at the answer
    // they already found, and popping the keyboard over it is a worse
    // welcome back than leaving the field as they left it.
    LaunchedEffect(Unit) {
        if (query.isBlank()) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new; onQueryChange(new) },
            placeholder = { Text("Search titles and summaries") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(Spacing.medium),
        )
        SearchResults(state, catalogState, watch, onPlay)
    }
}

/**
 * Wires [SearchViewModel] to [SearchScreen] — kept beside the screen rather
 * than in the branch that opens it, so that branch only ever wires a
 * position, never a ViewModel. When an answer belongs to this visit is
 * [searchVisitState]'s, shared with the television's search.
 */
@Composable
internal fun SearchBranch(
    query: String,
    catalogState: CatalogUiState,
    watch: WatchSnapshot,
    onQueryChange: (String) -> Unit,
    onPlay: (String) -> Unit,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    SearchScreen(
        query = query,
        state = searchVisitState(viewModel, query),
        catalogState = catalogState,
        watch = watch,
        onQueryChange = { text -> onQueryChange(text); viewModel.setQuery(text) },
        onPlay = onPlay,
    )
}

@Composable
private fun SearchResults(state: SearchUiState, catalogState: CatalogUiState, watch: WatchSnapshot, onPlay: (String) -> Unit) {
    if (state is SearchUiState.Failed) {
        CenteredMessage("Search failed: ${state.message}")
        return
    }
    val ready = state as? SearchUiState.Ready ?: return
    val catalogReady = catalogState is CatalogUiState.Ready
    val rows = remember(ready, catalogState) {
        if (catalogReady) searchRowsOf(ready.hits, catalogState) else emptyList()
    }
    when (searchResultsView(catalogReady, rows.isEmpty())) {
        SearchResultsView.LOADING -> CenteredMessage("Loading your library…")
        SearchResultsView.EMPTY -> CenteredMessage("No title, folder or summary in the library mentions that.")
        SearchResultsView.ROWS -> {
            val positions = remember(watch) { watch.progress.associateBy { it.setId } }
            val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = countOf(rows.size, "result"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.medium),
                ) {
                    items(rows, key = { it.set.setId }) { row ->
                        SearchResultRow(row, positions[row.set.setId], row.set.setId in watchedIds, onPlay)
                    }
                }
            }
        }
    }
}
