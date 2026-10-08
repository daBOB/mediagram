package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.asPaddingValues
import ui.tv.rememberStableRequester

/**
 * Six plates across, fixed rather than worked out from the window: every
 * television this draws for is 960dp wide whatever its pixels, so there is
 * no narrower screen to fit, and six is what that width holds at a size
 * read from across a room — the same six Home's rows show. Internal, not
 * private: `TvWallCells.kt`'s own `sectionCrossingsOf` needs this same
 * number, rather than a second one that could drift from it.
 */
internal const val Columns = 6

/**
 * How far past the screen's edge the wall keeps plates composed. A line of
 * six plates is a lot of work for a slow television box, and without this
 * the grid composes the line the remote scrolls into during the very frame
 * that reveals it — a visible hitch on every press. Composed ahead in the
 * idle time between frames instead, a line or so before it is needed; and
 * kept a line behind, so pressing Up does not rebuild what was just left.
 */
private val CacheAhead = 320.dp
private val CacheBehind = 320.dp

/**
 * One catalogue wall of plates — a shelf, a kept wall, a franchise, a
 * person's page — generic over whatever item type each of those already has.
 *
 * [key] is a stable identity per item: a grid keyed by position loses scroll
 * and focus state under a reorder.
 *
 * [ui.tv.chrome.LocalTvPagePadding] is applied as `contentPadding`, so a
 * focused plate scaled up at the grid's edge grows into it instead of being
 * clipped by an inset outside the viewport.
 *
 * [restoreKey] names the plate Back returns the remote to, since television
 * has no pointer position to remember; when it is not found focus falls
 * back to the first plate, so the wall is never left with nothing focused.
 *
 * [header] stands above the plates and scrolls with them ([headerHoldsNoStop]:
 * see [passesEntryOn]); [headings] start a
 * labelled line of plates before the item at each index (a genre page's
 * Movies, then its Series); [columns] is fewer than [Columns] for a wall of
 * wider art tiles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvWall(
    items: List<T>,
    key: (T) -> String,
    restoreKey: String?,
    onOpen: (T) -> Unit,
    header: (@Composable () -> Unit)? = null,
    headerHoldsNoStop: Boolean = false,
    headings: Map<Int, String> = emptyMap(),
    columns: Int = Columns,
    // Hoisted by a caller that also needs this wall's own scroll position —
    // a department page's own bar blend, read live through the same
    // instance rather than a second, disagreeing one this wall kept to
    // itself.
    gridState: LazyGridState = rememberLazyGridState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = CacheAhead, behind = CacheBehind) }),
    plate: @Composable (item: T, modifier: Modifier, onOpen: () -> Unit) -> Unit,
) {
    val takesFocus = LocalTakesArrivalFocus.current
    val focusRequester = remember { FocusRequester() }
    val wallHasFocus = remember { mutableStateOf(false) }
    // Provided around the grid, not inside `items { }`: the grid's own
    // scroll-into-view reads the spec ambient at its own composition position.
    // [header]'s cell resets to [defaultBringIntoView], or a horizontal row
    // inside it reads the bar clearance as a sideways offset.
    val defaultBringIntoView = LocalBringIntoViewSpec.current
    val barClearance = rememberTvBarClearanceBringIntoView()
    val cells = remember(items, header != null, headings) { cellsOf(items, header != null, headings) }
    val crossings = remember(items.size, headings, columns) { sectionCrossingsOf(items.size, headings, columns) }
    val crossingFocusRequesters = remember(crossings) { (crossings.up.keys + crossings.up.values).associateWith { FocusRequester() } }
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
    // laid out at least once. A plate on the first line scrolls to the very
    // top instead, so whatever heads the wall is on screen above it — unless
    // that header is taller than the screen, a show's art and a long
    // overview, which leaves the first plate below the fold and never laid
    // out, so the wall scrolls on down to it after all.
    // Keyed on the restore key as well as the index it resolves to: a caller
    // naming a new plate means "go there" even when it happens to sit at the
    // index the old one did — the next title after one taken off a list.
    LaunchedEffect(focusIndex, restoreKey) {
        if (focusIndex != null && takesFocus) {
            val cell = cells.indexOf(WallCell.Plate(focusIndex))
            if (focusIndex < columns) {
                gridState.scrollToItem(0)
                val shown = snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
                if (shown.none { it.index == cell }) gridState.scrollToItem(cell)
            } else {
                gridState.scrollToItem(cell)
            }
            focusRequester.requestFocus()
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides barClearance) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier =
                Modifier
                    .fillMaxSize()
                    // Only a wall with a hero over it: a kept wall leaves for the rail and keeps its place.
                    .toTopWhenLeftForTheBar { if (header != null) gridState.animateScrollToItem(0) }
                    .onFocusChanged { wallHasFocus.value = it.hasFocus },
            contentPadding = LocalTvPagePadding.current.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            items(
                items = cells,
                key = { cell ->
                    when (cell) {
                        WallCell.Header -> "header"
                        is WallCell.Heading -> "heading-${cell.label}"
                        is WallCell.Plate -> key(items[cell.index])
                    }
                },
                span = { cell -> if (cell is WallCell.Plate) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                // A plate scrolled off is recomposed as the next plate scrolled
                // on, never as a heading — the reuse a lazy grid only does
                // between items of one declared type.
                contentType = { cell -> cell::class },
            ) { cell ->
                when (cell) {
                    WallCell.Header ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) {
                            val plate = focusIndex?.let { cells.indexOf(WallCell.Plate(it)) } ?: -1
                            val passOn: suspend () -> Unit = { gridState.scrollToItem(plate).also { focusRequester.requestFocus() } }
                            Box(Modifier.passesEntryOn(headerHoldsNoStop && plate >= 0 && !wallHasFocus.value, passOn)) { header?.invoke() }
                        }
                    is WallCell.Heading -> TvBandHeading(title = cell.label, count = null)
                    is WallCell.Plate -> {
                        val item = items[cell.index]
                        val crossingRequester = crossingFocusRequesters[cell.index]
                        val upTarget = crossings.up[cell.index]?.let(crossingFocusRequesters::getValue)
                        val downTarget = crossings.down[cell.index]?.let(crossingFocusRequesters::getValue)
                        var itemModifier: Modifier = Modifier.focusRequester(rememberStableRequester(focusRequester.takeIf { cell.index == focusIndex }))
                        if (crossingRequester != null) itemModifier = itemModifier.focusRequester(crossingRequester)
                        if (upTarget != null || downTarget != null) {
                            itemModifier =
                                itemModifier.focusProperties {
                                    upTarget?.let { up = it }
                                    downTarget?.let { down = it }
                                }
                        }
                        plate(item, itemModifier) { onOpen(item) }
                    }
                }
            }
        }
    }
}

