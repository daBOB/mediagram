package ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import ui.common.rememberStatsPage
import ui.tv.catalog.TvStatsPage

/**
 * The Stats page as one frame on the library's stack, opened from the
 * rail's Stats row. Back leaves through [leave], and the catalogue puts the
 * remote back on that row (`TvStatsRailKey`). Nothing on the page opens
 * anything, so unlike `TvPreloadsFrame` it keeps no restore key of its own.
 */
@Composable
internal fun TvStatsFrame(
    catalogState: CatalogUiState,
    leave: () -> Unit,
) {
    BackHandler(onBack = leave)
    TvStatsPage(rememberStatsPage(catalogState))
}
