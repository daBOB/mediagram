package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.chrome.asPaddingValues

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
 * [ui.tv.chrome.LocalTvPagePadding] is applied as `contentPadding` rather
 * than a wrapping `Modifier.padding`, for the reason [ui.tv.TvShell]
 * documents on every other lazy wall this app draws: a focused plate
 * against the grid's edge grows under [ui.tv.TvFocus.Scale] into the
 * padding the grid itself reserves for it, instead of being clipped by a
 * fixed inset outside the scrollable viewport. Reading the ambient value
 * rather than the plain [designsystem.Overscan] it defaults to is what
 * lets this same wall draw both under the root catalogue's own chrome
 * (its content column's own start/top inset) and as a pushed frame's own
 * full-screen wall (plain [designsystem.Overscan] on every side) without
 * either caller having to say which one it is.
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
 * kept wall's "Title · n", a show's name and facts. [headings] start a new
 * line of plates under a label of its own before the item at each index — a
 * genre page's Movies, then its Series.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvWall(
    items: List<T>,
    key: (T) -> String,
    restoreKey: String?,
    onOpen: (T) -> Unit,
    header: (@Composable () -> Unit)? = null,
    headings: Map<Int, String> = emptyMap(),
    // Hoisted by a caller that also needs this wall's own scroll position —
    // a department page's own bar blend, read live through the same
    // instance rather than a second, disagreeing one this wall kept to
    // itself.
    gridState: LazyGridState = rememberLazyGridState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = CacheAhead, behind = CacheBehind) }),
    plate: @Composable (item: T, modifier: Modifier, onOpen: () -> Unit) -> Unit,
) {
    val takesFocus = LocalTakesArrivalFocus.current
    val focusRequester = remember { FocusRequester() }
    // Read once here, above the grid, and provided around it below —
    // `TvHome`'s own vertical list wraps its whole `LazyColumn` the same
    // way, not each item inside it: the grid's own scroll-into-view
    // machinery (what actually moves it up or down to keep a newly focused
    // cell visible) reads whatever spec is ambient at the grid's *own*
    // position in composition, not at each item's — a provider nested
    // inside `items { }` sits below that point and the grid's own
    // machinery never sees it, which is why an earlier version of this fix
    // (wrapping each cell instead) left Up still landing plates behind the
    // bar while Down happened to look clear by accident (a downward reveal
    // settles with the target's own bottom flush against the viewport's
    // bottom, which is nowhere near the bar to begin with). [header]'s own
    // cell resets back to [defaultBringIntoView] below, captured here
    // before the override exists: a horizontal row inside it (Anime's own
    // Continue watching) reads the vertical clearance as a horizontal
    // offset otherwise, reserving blank space on its own left the bar
    // never touches, for no reason — `TvHome`'s own doc on why it resets
    // the same way for its own bands.
    val defaultBringIntoView = LocalBringIntoViewSpec.current
    val barClearance = rememberTvBarClearanceBringIntoView()
    val cells = remember(items, header != null, headings) { cellsOf(items, header != null, headings) }
    val crossings = remember(items.size, headings) { sectionCrossingsOf(items.size, headings) }
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
    // Also on `takesFocus` itself: with this wall kept alive under a pushed
    // frame rather than rebuilt on every Back, `focusIndex`/`restoreKey`
    // rarely change across a visit, so nothing but this flip would ever
    // restart the request that puts the remote back once the cover lifts.
    LaunchedEffect(focusIndex, restoreKey, takesFocus) {
        if (focusIndex != null && takesFocus) {
            val cell = cells.indexOf(WallCell.Plate(focusIndex))
            if (focusIndex < Columns) {
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
            columns = GridCells.Fixed(Columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
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
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoView) { header?.invoke() }
                    is WallCell.Heading -> TvBandHeading(title = cell.label, count = null)
                    is WallCell.Plate -> {
                        val item = items[cell.index]
                        // Never omitted — see the same doc on `TvResumeCard`'s
                        // own `ownRequester`: a plate whose own index is not
                        // `focusIndex` right now still needs exactly one
                        // `focusRequester` in its own modifier chain, on every
                        // recomposition, or a restore whose own target moves
                        // (a reorder, a refresh) resets whichever plate is
                        // actually focused the moment the requester's presence
                        // toggles away from it.
                        val ownRequester = remember { FocusRequester() }
                        val crossingRequester = crossingFocusRequesters[cell.index]
                        val upTarget = crossings.up[cell.index]?.let(crossingFocusRequesters::getValue)
                        val downTarget = crossings.down[cell.index]?.let(crossingFocusRequesters::getValue)
                        var itemModifier: Modifier = Modifier.focusRequester(if (cell.index == focusIndex) focusRequester else ownRequester)
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

