package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import ui.catalog.CenteredMessage
import ui.chrome.BrowseActions
import ui.chrome.ProfileBarState
import ui.common.FrameResolution
import ui.common.LibraryPositions
import ui.common.MenuActions
import ui.common.resolveFrame

/** What the bar says while a frame's own key has not resolved to anything yet. */
internal const val LOADING = "…"

/**
 * Shows [content] once [resolved] answers, a loading message while the
 * catalog has not, or pops the frame once the catalog has and it still
 * does not — the viewer lands on whatever is next, one frame later.
 */
@Composable
internal fun <T> ResolvedBranch(
    resolved: T?,
    catalogState: CatalogUiState,
    loading: Destination,
    menu: MenuActions,
    profile: ProfileBarState,
    browse: BrowseActions,
    at: LibraryPositions,
    destinationOf: (T) -> Destination,
    content: @Composable (T) -> Unit,
) {
    when (val outcome = resolveFrame(resolved, catalogState is CatalogUiState.Ready)) {
        is FrameResolution.Resolved -> LibraryBranch(destinationOf(outcome.value), menu, profile, browse, at, at::pop) {
            content(outcome.value)
        }
        FrameResolution.Loading -> LibraryBranch(loading, menu, profile, browse, at, at::pop) {
            CenteredMessage("Loading your library…")
        }
        FrameResolution.Stale -> LaunchedEffect(Unit) { at.pop() }
    }
}
