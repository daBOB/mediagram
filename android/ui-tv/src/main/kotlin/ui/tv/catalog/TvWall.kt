package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import designsystem.Overscan
import designsystem.Spacing

/** Plates fit six across a 960dp-wide TV — see the phase's constraints for why this is fixed, not derived. */
private const val Columns = 6

/**
 * One catalogue wall, for every television screen a grid of plates is built
 * from — a shelf, a kept wall, a season wall, later a collection's wall too.
 * Generic over the item type so each of those can hand it whatever it
 * already has ([model.MediaSet], an entry, a season) without this file
 * needing to know the difference.
 *
 * [key] is a stable identity per item, the same reason `catalog.keyOf` exists
 * on the phone: a grid that keys by position rather than identity loses
 * scroll and focus state under a reorder.
 *
 * [Overscan] is applied as `contentPadding` rather than a wrapping
 * `Modifier.padding`, for the reason [ui.tv.TvShell] documents on every
 * other lazy wall this app draws: a focused plate against the grid's edge
 * grows under [ui.tv.TvFocus.Scale] into the padding the grid itself
 * reserves for it, instead of being clipped by a fixed inset outside the
 * scrollable viewport.
 *
 * Focus restoration is the one thing this wall does that the web reference
 * never had to: television has no pointer to remember a hover position for,
 * so a viewer who opened a title from partway down this wall and pressed
 * Back needs the remote to still be sitting on that same plate rather than
 * back at the top. [restoreKey] names that plate; when it isn't found (there
 * is none yet, or it no longer exists) focus falls back to the first plate,
 * so this wall is never left with nothing focused at all.
 */
@Composable
fun <T> TvWall(
    items: List<T>,
    key: (T) -> String,
    restoreKey: String?,
    onOpen: (T) -> Unit,
    plate: @Composable (item: T, modifier: Modifier, onOpen: () -> Unit) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val focusIndex =
        remember(items, restoreKey) {
            if (items.isEmpty()) {
                null
            } else {
                restoreKey?.let { wanted -> items.indexOfFirst { key(it) == wanted }.takeIf { it >= 0 } } ?: 0
            }
        }

    // Scrolled to before the focus request, not just requested outright: a
    // restored plate beyond the first screenful has no focusable node yet —
    // LazyVerticalGrid only composes what is within (or near) the viewport —
    // and a FocusRequester has nothing to attach to until its item has been
    // laid out at least once.
    LaunchedEffect(focusIndex) {
        if (focusIndex != null) {
            gridState.scrollToItem(focusIndex)
            focusRequester.requestFocus()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(Columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            val itemModifier = if (index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier
            plate(item, itemModifier) { onOpen(item) }
        }
    }
}
