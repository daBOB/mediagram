package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import catalog.Destination
import ui.catalog.CenteredMessage

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
    at: LibraryPositions,
    destinationOf: (T) -> Destination,
    content: @Composable (T) -> Unit,
) {
    when (val outcome = resolveFrame(resolved, catalogState is CatalogUiState.Ready)) {
        is FrameResolution.Resolved -> LibraryBranch(destinationOf(outcome.value), menu, profile, at, at::pop) {
            content(outcome.value)
        }
        FrameResolution.Loading -> LibraryBranch(loading, menu, profile, at, at::pop) {
            CenteredMessage("Loading your library…")
        }
        FrameResolution.Stale -> LaunchedEffect(Unit) { at.pop() }
    }
}
