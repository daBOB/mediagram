package ui

import androidx.compose.runtime.Composable
import catalog.CatalogUiState
import catalog.Destination
import ui.catalog.StatsScreen
import ui.chrome.BrowseActions
import ui.chrome.ProfileBarState

/**
 * The Stats page as one frame of its own on [at]'s stack, opened from the
 * rail, the header's icon row or a pushed frame's ⋮ the way Latest is.
 */
@Composable
internal fun StatsFrame(
    at: LibraryPositions,
    catalogState: CatalogUiState,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val state = rememberStatsPage(catalogState)
    LibraryBranch(Destination.Stats, menuActions, profileBar, browse, at, at::pop) {
        StatsScreen(state)
    }
}
