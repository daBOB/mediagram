package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.resolvableQueueRows
import kotlinx.coroutines.flow.map
import model.MediaSet
import player.TitlePreloadViewModel
import ui.tv.catalog.TvTitlePreloadUi

/**
 * As [ui.LibraryFlowBranches]' own `rememberFilmPreloadUi` — the same
 * ViewModel, this surface's own plain-data shape. `null` while [set]'s own
 * size is not yet known, and once for the frame before `stateOf`'s first
 * real emission — see that function's own doc for both reasons in full.
 * Split out of `TvLibraryCatalogFrames.kt` purely to keep that file under
 * the project's line guideline, the same reason `di/ActivePlayback.kt`
 * exists.
 *
 * [viewModel]/[set]'s id/[set]'s own size key both `remember` blocks below:
 * without them the same over-polling bug the phone's own doc names would
 * follow here too, since `stateOf`/`serverLine` are plain cold `Flow`s and
 * `collectAsStateWithLifecycle` restarts on a new instance of one.
 *
 * [catalogState] filters what `queuedAhead` reads before it ever names
 * another film — a kids profile must not learn a grown-up's own queued
 * title through "Queued · after …" either.
 */
@Composable
internal fun rememberTvFilmPreloadUi(set: MediaSet, catalogState: CatalogUiState, onOpenStorage: () -> Unit): TvTitlePreloadUi? {
    if (set.totalBytes <= 0) return null
    val viewModel: TitlePreloadViewModel = hiltViewModel()
    val state by
        remember(viewModel, set.setId, set.totalBytes) { viewModel.stateOf(set.setId, set.totalBytes) }
            .collectAsStateWithLifecycle(initialValue = null)
    val serverLine by
        remember(viewModel, set.setId, set.totalBytes) { viewModel.serverLine(set.setId, set.totalBytes) }
            .collectAsStateWithLifecycle(initialValue = null)
    val resolvableRows = remember(viewModel, catalogState) { viewModel.queueRows.map { catalogState.resolvableQueueRows(it) } }
    val queuedAheadLabel by
        remember(viewModel, set.setId, set.totalBytes, resolvableRows) { viewModel.queuedAhead(set.setId, set.totalBytes, resolvableRows) }
            .collectAsStateWithLifecycle(initialValue = null)
    val needsSpaceBudgetBytes by
        remember(viewModel, set.setId, set.totalBytes) { viewModel.needsSpaceBudget(set.setId, set.totalBytes) }
            .collectAsStateWithLifecycle(initialValue = null)
    val currentState = state ?: return null
    return TvTitlePreloadUi(
        state = currentState,
        serverLine = serverLine,
        onToggle = { viewModel.toggle(set.setId, set.title, set.totalBytes, currentState) },
        onRemove = { viewModel.remove(set.setId) },
        onOpenStorage = onOpenStorage,
        queuedAheadLabel = queuedAheadLabel,
        needsSpaceBudgetBytes = needsSpaceBudgetBytes,
    )
}
