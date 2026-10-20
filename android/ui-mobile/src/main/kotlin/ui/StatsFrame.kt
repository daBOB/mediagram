package ui

import androidx.compose.runtime.Composable
import ui.catalog.StatsScreen
import ui.chrome.BrowseActions
import ui.chrome.ProfileBarState
import ui.common.LibraryPositions
import ui.common.MenuActions
import ui.common.rememberStatsPage

/**
 * The Stats page as one frame of its own on [at]'s stack, opened from the
 * rail, the header's icon row or a pushed frame's ⋮ the way Latest is.
 */
@Composable
internal fun StatsFrame(
    at: LibraryPositions,
    menuActions: MenuActions,
    profileBar: ProfileBarState,
    browse: BrowseActions,
) {
    val state = rememberStatsPage()
    LibraryBranch(Destination.Stats, menuActions, profileBar, browse, at, at::pop) {
        StatsScreen(state)
    }
}
