package ui.tv.chrome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.LocalTvStyle
import designsystem.Overscan
import designsystem.TvStyle
import designsystem.tvDp
import ui.common.RailItem
import ui.tv.hasRail
import ui.tv.profile.TvChosenProfile

/** The bar's own row height, before its top inset: the web's `--masthead-height` (`theme.css:114`). */
internal val TvDepartmentsBarHeight = 76.tvDp

/** The page's and the bar's side gutter, off the collapsed rail's edge at the start and off the screen's at the end: the web's `--gutter`, 3.2vw at 1280px (`theme.css:112`), the same on both sides as the web's `main` and `.masthead`. */
internal val TvContentGutter = 40.96.tvDp

/**
 * Where the bar and the content both start, regardless of whether the rail
 * is open — it overlays past this line rather than pushing it, so this
 * never changes with the rail's own width. [TvContentGutter] is layered on
 * top of this, in [LocalTvPagePadding] and the bar's start, as the web's
 * `main` and `.masthead` both pad by `--gutter` off the rail.
 */
internal val TvContentStart = RailCollapsedWidth

/**
 * The chrome around the catalogue's own body: [TvLibraryRail] overlaying the
 * start edge, the style's bar ([TvChromeBar]) drawn opaque above [content],
 * and the Back chain — content leaves for the selected pill (or, on a kept
 * wall, the rail's own active row); the bar leaves for the rail; the rail is
 * left unhandled, so a further Back closes the app.
 *
 * That last step only holds once the remote has genuinely reached the rail:
 * the three focus flags all start `false` on every fresh mount, and arrival
 * focus lands a frame after the [BackHandler]s register. A Back in that gap
 * would read as resting on the rail and finish the activity uninvited; the
 * last [BackHandler] below exists only to absorb it, never to redirect focus.
 *
 * Apple TV and Google TV styles draw no rail ([TvStyle.hasRail]): the page
 * starts at the screen's overscan, Left at its start goes nowhere, and
 * [TvChromeFocus.railTarget] stands in for every rail row — the avatar,
 * whose menu lists them, or Google TV's gear for Settings. Back from the bar
 * then closes the app, as Back from the rail does here.
 *
 * [focus] is built by the caller so a restore key it already knows about
 * (`masthead:search`, `rail:latest`…) can drive the very requesters this
 * composable renders onto.
 *
 * [content] draws under the bar at [LocalTvPagePadding]'s own inset; the rail
 * is a sibling overlay in this [Box], so opening it never remeasures the page.
 * [blend] is the bar's opacity: `1f` for every tab without a hero to bleed
 * under; Home, over its own magazine cover, passes less.
 */
@Composable
internal fun TvLibraryChrome(
    pills: List<String>,
    selectedPill: Int,
    onSelectPill: (Int) -> Unit,
    railActive: RailItem?,
    counts: ChromeCounts,
    tally: List<String>,
    onRailSelect: (RailItem) -> Unit,
    onSearch: () -> Unit,
    profile: TvChosenProfile,
    onMenu: () -> Unit,
    focus: TvChromeFocus,
    modifier: Modifier = Modifier,
    blend: Float = 1f,
    content: @Composable () -> Unit,
) {
    var barHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    var railHasFocus by remember { mutableStateOf(false) }
    // Which side of the bar the remote was last on, so the rail's Right can
    // go back there. Not a `focusRestorer` over bar and page together: that
    // restores into the first child with a remembered focus, and the bar
    // always has one — it turned a page's own arrival (Home back from the
    // player) and the rail's Right from a plate into the bar's pill.
    var lastInBar by remember { mutableStateOf(false) }

    val hasRail = LocalTvStyle.current.hasRail

    fun railArrivalTarget(): FocusRequester = focus.railTarget(railActive ?: RailItem.MY_LIST)

    // "Back: page -> the selected pill (no pill selected, i.e. a kept wall
    // -> its rail row)" — enabled only while the remote is actually inside
    // content.
    BackHandler(enabled = contentHasFocus) {
        if (selectedPill >= 0) focus.selectedPillFocus.requestFocus() else railArrivalTarget().requestFocus()
    }
    // "bar (pill, search, avatar) -> rail" — the rail's own active row,
    // or My List with nothing kept showing.
    BackHandler(enabled = barHasFocus && hasRail) {
        railArrivalTarget().requestFocus()
    }
    // The mount gap the class doc names: nothing has focus yet, which looks
    // like resting on the rail. Absorbed, not redirected — arrival focus
    // settles it a frame later.
    BackHandler(enabled = !contentHasFocus && !barHasFocus && !railHasFocus) {}

    // `top` is the bar's rendered height: it draws over this same region, so
    // a page's first stop needs that much clearance to sit below it.
    val padding =
        remember(hasRail) {
            val top = TvDepartmentsBarHeight + Overscan.vertical
            // With no rail to stand off, the page keeps the screen's own overscan on both sides.
            if (hasRail) {
                TvPagePadding(start = TvContentGutter, top = top, end = TvContentGutter, bottom = Overscan.vertical)
            } else {
                TvPagePadding(start = Overscan.horizontal, top = top, end = Overscan.horizontal, bottom = Overscan.vertical)
            }
        }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = if (hasRail) TvContentStart else 0.dp)
                    // The one exit rule for bar and page both: a focus target
                    // keeps a single `onExit`, and this outer one is the one it
                    // keeps, so a second rule on the page's own Box below never
                    // ran — Up out of a page went wherever geometry pointed.
                    .focusProperties {
                        onExit = {
                            when (requestedFocusDirection) {
                                FocusDirection.Left -> if (hasRail) railArrivalTarget().requestFocus() else if (contentHasFocus) cancelFocusChange()
                                FocusDirection.Up ->
                                    if (contentHasFocus) (if (selectedPill >= 0) focus.selectedPillFocus else railArrivalTarget()).requestFocus()
                                else -> Unit
                            }
                        }
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focus.contentFocus)
                        .onFocusChanged { state ->
                            contentHasFocus = state.hasFocus
                            if (state.hasFocus) lastInBar = false
                        },
            ) {
                CompositionLocalProvider(LocalTvPagePadding provides padding, content = content)
            }
            TvChromeBar(
                pills = pills,
                selected = selectedPill,
                onSelect = onSelectPill,
                onSearch = onSearch,
                profile = profile,
                onMenu = onMenu,
                onRailSelect = onRailSelect,
                focus = focus,
                blend = blend,
                modifier =
                    Modifier.align(Alignment.TopStart).onFocusChanged { state ->
                        barHasFocus = state.hasFocus
                        if (state.hasFocus) lastInBar = true
                    },
            )
        }

        if (hasRail) {
            TvLibraryRail(
                active = railActive,
                counts = counts,
                tally = tally,
                rowRequesters = focus.railRowFocus,
                onSelect = onRailSelect,
                // The page's own restorer puts the remote back on the plate it left.
                onRight = { (if (lastInBar && selectedPill >= 0) focus.selectedPillFocus else focus.contentFocus).requestFocus() },
                onHasFocusChanged = { railHasFocus = it },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}
