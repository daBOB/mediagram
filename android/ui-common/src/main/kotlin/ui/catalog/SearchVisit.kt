package ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.SearchUiState
import catalog.SearchViewModel

/**
 * What one visit to a search screen shows — shared by the phone's and the
 * television's, which differ in how they draw an answer but not in when
 * one belongs to them.
 *
 * [SearchViewModel] outlives one visit to the screen — it is scoped above
 * it, the same as every other ViewModel the library reaches for — so
 * [SearchViewModel.open] runs once on the way in to make this visit's
 * [query] its own, rather than going on showing whatever a previous visit,
 * possibly for an entirely different query, last found.
 *
 * `opened` gates the very first frame on that call having actually run:
 * [LaunchedEffect] fires after composition, one frame later than
 * [collectAsStateWithLifecycle]'s own first read, which would otherwise
 * read whatever the ViewModel still held from before [SearchViewModel.open]
 * had the chance to clear it.
 */
@Composable
fun searchVisitState(viewModel: SearchViewModel, query: String): SearchUiState {
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.open(query)
        opened = true
    }
    val collected by viewModel.state.collectAsStateWithLifecycle()
    return if (opened) collected else SearchUiState.Idle
}
