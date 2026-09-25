package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import catalog.CatalogUiState
import ui.catalog.CenteredMessage

/** What the bar says while a frame's own key has not resolved to anything yet. */
internal const val LOADING = "…"

/**
 * What a frame whose key does not resolve draws — pure, so the rule is
 * tested without a `Composable`. [FrameKind.TITLE], [FrameKind.SEASON],
 * [FrameKind.COLLECTION] and [FrameKind.LIST] all read their own value
 * from a lookup against the catalog, and used to draw nothing at all —
 * no bar, no back handler — when it came back `null`, so back finished
 * the Activity instead of leaving the screen. That happened on a restore
 * landing here before the catalog loaded, or on a stale key left over
 * once whatever it named was deleted elsewhere.
 */
internal sealed interface FrameResolution<out T> {
    data class Resolved<T>(val value: T) : FrameResolution<T>

    /** Not yet answered by the catalog — "not yet", not "gone". */
    data object Loading : FrameResolution<Nothing>

    /** The catalog answered and the key still names nothing — gone for good. */
    data object Stale : FrameResolution<Nothing>
}

internal fun <T> resolveFrame(value: T?, catalogReady: Boolean): FrameResolution<T> = when {
    value != null -> FrameResolution.Resolved(value)
    !catalogReady -> FrameResolution.Loading
    else -> FrameResolution.Stale
}

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
