package ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import catalog.CatalogTabs
import kotlin.math.roundToInt
import ui.BrowseActions
import ui.MenuActions
import ui.ProfileBarState
import ui.railSelect
import ui.setup.StartOverConfirmation

/**
 * The root library's own top-level layout: [LibraryRail] beside the content
 * on EXPANDED with [DepartmentsBar] laid over it, [CompactLibraryHeader]
 * above the content otherwise — the web's rail-plus-masthead pair, read as
 * one frame rather than two, since which of them draws depends on the same
 * window width either way.
 *
 * [content] has exactly one call site below, and which sibling stands beside
 * it is the only thing that changes with width — a width-class change (a
 * tablet rotated) moves `content()` to a different slot in the composition
 * otherwise, and every `remember`/`rememberSaveable` under it — a scroll
 * position, a title already fetched — is lost with it.
 *
 * [homeScrollState] is the Home tab's own grid state, hoisted up here so the
 * departments bar can read where the page actually is rather than tracking
 * scroll deltas of its own; [hasCover] is whether Home actually drew a cover
 * to bleed the bar's translucent opening state over — with nothing to bleed
 * over, Home is padded like every other department instead.
 *
 * [chosenTab]/[tabs]/[visible] are [ui.LibraryBranches]'s own full index
 * space; this only reads [chosenTab] against [tabs.firstKept] to know
 * whether Continue or Watchlist is the rail's own active row, and against
 * `0` to know whether [content] is Home.
 */
@Composable
internal fun LibraryHome(
    tabs: CatalogTabs,
    visible: List<Int>,
    chosenTab: Int,
    onTabChange: (Int) -> Unit,
    browse: BrowseActions,
    menu: MenuActions,
    profile: ProfileBarState,
    onSearch: () -> Unit,
    homeScrollState: LazyGridState,
    hasCover: Boolean,
    content: @Composable () -> Unit,
) {
    var askingStartOver by remember { mutableStateOf(false) }
    val rail = LocalRailData.current
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val isHome = chosenTab == 0
    // Only Home, only with a cover to show through it, only on EXPANDED where
    // the bar never itself moves — everywhere else the chrome is opaque from
    // the start and content is padded clear of it instead of drawn under it.
    val bleed = expanded && isHome && hasCover
    val pills = remember(tabs, visible, rail.counts) { visible.map { i -> DepartmentPill(tabs.titles[i], rail.counts.departmentCount(tabs.titles[i])) } }
    // -1 when chosenTab is a kept wall (My List/Continue) rather than a
    // department — no pill is the current one then, not Home by default.
    val selectedPill = visible.indexOf(chosenTab)
    val activeRailItem =
        when (chosenTab) {
            tabs.firstKept -> RailItem.CONTINUE_WATCHING
            tabs.firstKept + 1 -> RailItem.MY_LIST
            else -> null
        }
    val onRailSelect: (RailItem) -> Unit = { item -> railSelect(item, browse, menu) }
    val onAskStartOver = { askingStartOver = true }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val expandedChromeHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DepartmentsBarHeight

    Row(Modifier.fillMaxSize()) {
        if (expanded) {
            LibraryRail(active = activeRailItem, onHome = rail.onHome, onSelect = onRailSelect, modifier = Modifier.fillMaxHeight())
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewportPx = with(density) { maxHeight.toPx() }
            // Read from the Home grid's own position rather than tracked
            // scroll deltas: a deep position restored after a back-navigate,
            // or reached by scrolling up from further down, both read right
            // the moment this recomposes, with nothing of its own to reset.
            val blend by remember(isHome, viewportPx) {
                derivedStateOf {
                    if (!isHome) 1f else coverBlend(homeScrollState.firstVisibleItemIndex, homeScrollState.firstVisibleItemScrollOffset, viewportPx)
                }
            }
            // Created once and read through the same `var ... by remember`
            // delegate on every scroll callback, rather than rebuilt each
            // time `headerOffsetPx` itself changes — which is every frame
            // of a drag.
            val headerConnection =
                remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val next = (headerOffsetPx + available.y).coerceIn(-headerHeightPx.toFloat(), 0f)
                            val consumed = next - headerOffsetPx
                            headerOffsetPx = next
                            return Offset(0f, consumed)
                        }
                    }
                }
            val bodyModifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .then(if (expanded) Modifier else Modifier.nestedScroll(headerConnection))
            // One call site for `content()` below, whichever branch this
            // is — only the modifier that positions it depends on
            // [expanded]. Two call sites (one per branch) would be the
            // same H1 defect over again: a width-class change would move
            // `content()` itself to a different slot in the composition
            // and lose every `remember`/`rememberSaveable` under it.
            val contentModifier =
                if (expanded) {
                    Modifier.padding(top = if (bleed) 0.dp else expandedChromeHeight)
                } else {
                    // The header hides by moving up, not by shrinking, so
                    // its own height is still what content is padded by —
                    // measured here, in the layout phase, rather than read
                    // from a plain top padding that would ignore the
                    // header's current offset and leave a gap the size of
                    // a hidden header behind.
                    Modifier.layout { measurable, constraints ->
                        val top = (headerHeightPx + headerOffsetPx).roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - top).coerceAtLeast(0)))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top) }
                    }
                }
            CompositionLocalProvider(LocalTopChrome provides if (bleed) expandedChromeHeight else 0.dp) {
                Box(bodyModifier) {
                    Box(contentModifier) { content() }
                    if (expanded) {
                        DepartmentsBar(
                            pills = pills, selected = selectedPill, onSelect = { onTabChange(visible[it]) }, onSearch = onSearch,
                            profile = profile, menu = menu, onAskStartOver = onAskStartOver, blend = blend,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    } else {
                        CompactLibraryHeader(
                            pills = pills, selectedPill = selectedPill, onSelectPill = { onTabChange(visible[it]) }, onHome = rail.onHome,
                            activeRailItem = activeRailItem, onRailSelect = onRailSelect, onSearch = onSearch,
                            profile = profile, menu = menu, onAskStartOver = onAskStartOver,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .onGloballyPositioned { headerHeightPx = it.size.height }
                                    .offset { IntOffset(0, headerOffsetPx.roundToInt()) },
                        )
                    }
                }
            }
        }
    }

    StartOverConfirmation(asking = askingStartOver, onDismiss = { askingStartOver = false }, onConfirm = menu.onStartOver)
}
