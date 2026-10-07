package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogUiState
import catalog.heldFilms
import catalog.resolvableQueueRows
import player.TitlePreloadViewModel
import ui.common.LibraryPositions
import ui.tv.catalog.TvPreloadsPage

/**
 * What is preloading, queued, or already fully on this device — Android
 * only, follows [TvGenresFrame]/[TvLatestFrame]'s own shape: one frame of
 * its own on [at]'s stack. [TitlePreloadViewModel] is the same instance a
 * film page resolves through `hiltViewModel()`, so this reads the one
 * engine every other screen already does.
 *
 * [catalogState] filters the engine's own rows to what this profile's
 * catalogue can resolve before they reach the page — a kids profile must
 * never see a grown-up's own preload, the same reason its shelves never
 * carry that title either.
 */
@Composable
internal fun TvPreloadsFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    restore: TvRestoreKeys,
    here: Int,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    val viewModel: TitlePreloadViewModel = hiltViewModel()
    val rawRows by viewModel.queueRows.collectAsStateWithLifecycle(initialValue = emptyList())
    val rows = remember(rawRows, catalogState) { catalogState.resolvableQueueRows(rawRows) }
    val heldFilms = remember(catalogState) { catalogState.heldFilms() }
    TvPreloadsPage(
        rows = rows,
        heldFilms = heldFilms,
        onOpenTitle = { setId ->
            restore.opened(here, setId)
            at.openTitle(setId)
        },
        onCancel = viewModel::cancel,
        onRemove = viewModel::remove,
        onResume = { row -> viewModel.resume(row.setId, row.title, row.totalBytes) },
        restoreKey = restore.of(here),
    )
}
