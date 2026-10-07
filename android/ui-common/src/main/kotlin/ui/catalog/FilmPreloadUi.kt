package ui.catalog

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
import playback.FilmPreloadState

/**
 * What a title page needs to draw a film's Preload control — plain data
 * and callbacks, so the page itself (the phone's `TitleDetailScreen`, the
 * TV's `TvTitlePage`) never touches Hilt or the engine. The wiring layer
 * (the phone's `LibraryTitleBranches`, the TV's `TvLibraryCatalogFrames`)
 * builds it through [rememberFilmPreloadUi].
 */
data class TitlePreloadUi(
    val state: FilmPreloadState,
    val serverLine: String?,
    val onToggle: () -> Unit,
    val onRemove: () -> Unit,
    val onOpenStorage: () -> Unit,
    /** What a [FilmPreloadState.Queued] film's own label adds beyond "Queued" — see [player.queuedAheadLabel]. */
    val queuedAheadLabel: String? = null,
    /** The live cache budget a [FilmPreloadState.NeedsSpace] film's own label names — see [player.TitlePreloadViewModel.needsSpaceBudget]. */
    val needsSpaceBudgetBytes: Long? = null,
)

/**
 * A film's own Preload state, plus the actions its control needs, for both
 * surfaces' title pages.
 *
 * `null` while a film's size is not yet known ([MediaSet.totalBytes] `<= 0`
 * — the control has nothing to preload towards) and, once, for the one
 * frame before [TitlePreloadViewModel.stateOf]'s first real emission
 * arrives: an `initialValue` of `Idle(0, total)` there would otherwise
 * flash "Preload · x GB" on every page open, even for a film already
 * `Done` or mid-`Running`.
 *
 * The view model, [set]'s own id and its own size key the `remember`
 * blocks below — without them, `stateOf`/`serverLine` (both plain cold
 * `Flow`s, a fresh one on every call) would be invoked again on every
 * recomposition, and `collectAsStateWithLifecycle` restarts its collection
 * whenever the `Flow` instance it was given changes identity. Left
 * unkeyed, this had the server line polling on every progress tick
 * (`ProgressThrottle`'s ~4/s) instead of every 5s.
 *
 * [catalogState] filters what [queuedAhead][TitlePreloadViewModel.queuedAhead]
 * reads before it ever names another film — a kids profile must not learn
 * a grown-up's own queued title through "Queued · after …" either.
 */
@Composable
fun rememberFilmPreloadUi(set: MediaSet, catalogState: CatalogUiState, onOpenStorage: () -> Unit): TitlePreloadUi? {
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
    return TitlePreloadUi(
        state = currentState,
        serverLine = serverLine,
        onToggle = { viewModel.toggle(set.setId, set.title, set.totalBytes, currentState) },
        onRemove = { viewModel.remove(set.setId) },
        onOpenStorage = onOpenStorage,
        queuedAheadLabel = queuedAheadLabel,
        needsSpaceBudgetBytes = needsSpaceBudgetBytes,
    )
}
