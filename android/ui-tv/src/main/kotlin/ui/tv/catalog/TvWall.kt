package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 *
 * [header] is whatever stands above the plates and scrolls with them — a
 * kept wall's "Title · n", a show's name and facts — and [section] splits
 * one wall into headed runs, as the Kids wall does, without a second grid
 * whose focus would have to be handed across.
 */
@Composable
fun <T> TvWall(
    items: List<T>,
    key: (T) -> String,
    restoreKey: String?,
    onOpen: (T) -> Unit,
    header: (@Composable () -> Unit)? = null,
    section: ((T) -> String)? = null,
    plate: @Composable (item: T, modifier: Modifier, onOpen: () -> Unit) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val cells = remember(items, header != null, section) { cellsOf(items, header != null, section) }
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
    // laid out at least once. The first plate scrolls to the very top
    // instead, so whatever heads the wall is on screen above it.
    // Keyed on the restore key as well as the index it resolves to: a caller
    // naming a new plate means "go there" even when it happens to sit at the
    // index the old one did — the next title after one taken off a list.
    LaunchedEffect(focusIndex, restoreKey) {
        if (focusIndex != null) {
            gridState.scrollToItem(if (focusIndex == 0) 0 else cells.indexOf(WallCell.Plate(focusIndex)))
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
        items(
            items = cells,
            key = { cell ->
                when (cell) {
                    WallCell.Header -> "header"
                    is WallCell.Heading -> "heading-${cell.title}"
                    is WallCell.Plate -> key(items[cell.index])
                }
            },
            span = { cell -> if (cell is WallCell.Plate) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        ) { cell ->
            when (cell) {
                WallCell.Header -> header?.invoke()
                is WallCell.Heading -> TvSectionHeading(cell.title)
                is WallCell.Plate -> {
                    val item = items[cell.index]
                    val itemModifier = if (cell.index == focusIndex) Modifier.focusRequester(focusRequester) else Modifier
                    plate(item, itemModifier) { onOpen(item) }
                }
            }
        }
    }
}

/**
 * One line of a wall as the grid lays it out: the optional header across
 * the top, a section's heading across a whole line, or one plate — so a
 * plate's place in the grid is looked up here rather than worked out again
 * wherever the grid has to be scrolled to one.
 */
private sealed interface WallCell {
    data object Header : WallCell

    data class Heading(val title: String) : WallCell

    data class Plate(val index: Int) : WallCell
}

/** A heading goes in wherever [section] changes from the plate before, so a caller only says which section each item is in. */
private fun <T> cellsOf(
    items: List<T>,
    hasHeader: Boolean,
    section: ((T) -> String)?,
): List<WallCell> =
    buildList {
        if (hasHeader) add(WallCell.Header)
        var current: String? = null
        items.forEachIndexed { index, item ->
            section?.invoke(item)?.let { title ->
                if (title != current) add(WallCell.Heading(title))
                current = title
            }
            add(WallCell.Plate(index))
        }
    }
