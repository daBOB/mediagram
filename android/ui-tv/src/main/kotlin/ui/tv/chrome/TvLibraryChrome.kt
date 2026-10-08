package ui.tv.chrome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.ChromeCounts
import designsystem.Overscan
import ui.common.RailItem
import ui.tv.profile.TvChosenProfile

/** The bar's own row height, before its top inset — the tablet's own `DepartmentsBarHeight` (`ChromeControls.kt`), reused since both draw the same pills. */
internal val TvDepartmentsBarHeight = 76.dp

/** The gutter between the collapsed rail's own edge and whatever the content or the bar draws next — [Overscan.horizontal] is the screen's own safe margin from x=0; this is the rail's own, measured from its own far edge instead. */
internal val TvContentGutter = 32.dp

/** The rail's own collapsed width — [TvLibraryRail] is the one composable that draws it; declared here too, ahead of [TvContentStart], only so the outer column knows where to start clear of it. */
private val RailCollapsedWidth = 96.dp

/**
 * Where the bar and the content both start, regardless of whether the rail
 * is open — it overlays past this line rather than pushing it, so this
 * never changes with the rail's own width. [TvContentGutter] is layered on
 * top of this, in [LocalTvPagePadding] alone, not here: a focused plate's
 * own left edge needs the extra 32dp room [TvContentGutter] names, but a
 * cramped 96dp is already clear of the collapsed rail's own icons.
 */
internal val TvContentStart = RailCollapsedWidth

/**
 * What a page under the chrome is padded by, read instead of [Overscan]
 * directly by every composable [TvCatalogBody] can show — [TvWall],
 * [TvHome], a kept wall, the Collections and Movies department pages.
 * Provided only by [TvLibraryChrome]; every pushed frame (a title, Latest,
 * Search…) never sits under it, so those composables read the default
 * here instead — plain [Overscan] on every side, the inset they always
 * used — without any of them needing to ask which is true.
 */
internal data class TvPagePadding(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

internal val LocalTvPagePadding =
    compositionLocalOf { TvPagePadding(Overscan.horizontal, Overscan.vertical, Overscan.horizontal, Overscan.vertical) }

/** [TvPagePadding] as a [PaddingValues] — every reader wants it as one, not as four separate [Dp]s. */
internal fun TvPagePadding.asPaddingValues(): PaddingValues = PaddingValues(start = start, top = top, end = end, bottom = bottom)

/**
 * Everything [TvLibraryChrome] hands its own rows and pills their focus
 * through, and everything a restore key resolves to once it names one of
 * them — built once per [ui.tv.catalog.TvCatalogScreen] by
 * [rememberTvChromeFocus] so a screen coming back to "the search button"
 * or "the Latest row" always reaches the very requester [TvLibraryChrome]
 * itself renders onto.
 */
internal class TvChromeFocus(
    val selectedPillFocus: FocusRequester,
    val searchFocus: FocusRequester,
    val menuButtonFocus: FocusRequester,
    val avatarFocus: FocusRequester,
    val railRowFocus: Map<RailItem, FocusRequester>,
    val contentFocus: FocusRequester,
)

@Composable
internal fun rememberTvChromeFocus(): TvChromeFocus =
    TvChromeFocus(
        selectedPillFocus = remember { FocusRequester() },
        searchFocus = remember { FocusRequester() },
        menuButtonFocus = remember { FocusRequester() },
        avatarFocus = remember { FocusRequester() },
        railRowFocus = remember { RailItem.entries.associateWith { FocusRequester() } },
        contentFocus = remember { FocusRequester() },
    )

/**
 * The chrome around the catalogue's own body: [TvLibraryRail] overlaying
 * the start edge, [TvDepartmentsBar] drawn opaque above [content], and the
 * three-region Back chain the plan settled on — content leaves for the
 * selected pill (or, on a kept wall, the rail's own active row); the bar
 * leaves for the rail; the rail is left unhandled, so a further Back closes
 * the app the way Back at the top of any television app does.
 *
 * That last step only holds once the remote has genuinely reached the
 * rail: [contentHasFocus]/[barHasFocus] (and the rail's own, mirrored into
 * [railHasFocus]) all start `false` on every fresh mount of this
 * composable — arrival focus only lands once whichever page below calls
 * `requestFocus()` from its own `LaunchedEffect`, at least one frame after
 * this composable's own `BackHandler`s have already registered. A Back
 * arriving in that gap — this chrome (re)appearing under a stray or
 * doubled key event, with nothing pressed since — would otherwise be
 * indistinguishable from genuinely resting on the rail and finish the
 * activity uninvited; the fourth [BackHandler] below exists only to catch
 * that gap, never to redirect focus anywhere on its own.
 *
 * [focus] is [rememberTvChromeFocus]'s own bundle — built by the caller so
 * a restore key it already knows about (`masthead:search`, `rail:latest`…)
 * can drive the very requesters this composable renders onto, without this
 * composable needing to know what a restore key even is.
 *
 * [content] draws under the bar at [LocalTvPagePadding]'s own inset; it
 * never repaints or remeasures when the rail opens over it, since the rail
 * is a sibling overlay in this [Box], not a sibling in a [Row] the rail's
 * own width could push against.
 *
 * [blend] passes straight through to [TvDepartmentsBar]'s own parameter of
 * the same name — `1f` (opaque) for every tab without a hero to bleed
 * under; Home, over its own magazine cover, is the first real caller of
 * anything less.
 */
@Composable
internal fun TvLibraryChrome(
    pills: List<TvDepartmentPill>,
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

    fun railArrivalTarget(): FocusRequester = focus.railRowFocus.getValue(railActive ?: RailItem.MY_LIST)

    // "Back: page -> the selected pill (no pill selected, i.e. a kept wall
    // -> its rail row)" — enabled only while the remote is actually inside
    // content.
    BackHandler(enabled = contentHasFocus) {
        if (selectedPill >= 0) focus.selectedPillFocus.requestFocus() else railArrivalTarget().requestFocus()
    }
    // "bar (pill, search, avatar, ⋮) -> rail" — the rail's own active row,
    // or My List with nothing kept showing.
    BackHandler(enabled = barHasFocus) {
        railArrivalTarget().requestFocus()
    }
    // Rail: still no handler that goes anywhere — Back there falls
    // through to the activity, "the activity finishes" at the top of this
    // app, exactly as the plan asks.
    //
    // What *is* caught here is the narrow gap the class doc above names:
    // this chrome just (re)mounted and none of the three regions has
    // taken arrival focus yet, so every one of `contentHasFocus`,
    // `barHasFocus` and `railHasFocus` still reads its initial `false` —
    // the same shape as "genuinely resting on the rail". Absorbing it
    // (never redirecting: the arrival-focus effect already queued below
    // settles this on its own a frame later) is what keeps a Back that
    // lands in that gap from reading as the rail's own "close the app".
    BackHandler(enabled = !contentHasFocus && !barHasFocus && !railHasFocus) {}

    // `top` matches the bar's own rendered height: the bar draws opaquely
    // over this same region (a `Box`, not a `Column` — see the doc above),
    // so a page's first stop needs exactly this much clearance to sit
    // below it rather than under it; a lower plate scrolling up into that
    // same band is then hidden by the bar drawn on top of it, one hero's
    // worth of bleed away from becoming visible instead once a cover here
    // has one to bleed under it.
    val padding = remember { TvPagePadding(start = TvContentGutter, top = TvDepartmentsBarHeight + Overscan.vertical, end = Overscan.horizontal, bottom = Overscan.vertical) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = TvContentStart)
                    // The one exit rule for bar and page both: a focus target
                    // keeps a single `onExit`, and this outer one is the one it
                    // keeps, so a second rule on the page's own Box below never
                    // ran — Up out of a page went wherever geometry pointed.
                    .focusProperties {
                        onExit = {
                            when (requestedFocusDirection) {
                                FocusDirection.Left -> railArrivalTarget().requestFocus()
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
            TvDepartmentsBar(
                pills = pills,
                selected = selectedPill,
                onSelect = onSelectPill,
                onSearch = onSearch,
                profile = profile,
                onMenu = onMenu,
                downTarget = focus.contentFocus,
                selectedPillFocus = focus.selectedPillFocus,
                searchFocus = focus.searchFocus,
                menuFocus = focus.menuButtonFocus,
                avatarFocus = focus.avatarFocus,
                blend = blend,
                modifier = Modifier.align(Alignment.TopStart).onFocusChanged { state ->
                        barHasFocus = state.hasFocus
                        if (state.hasFocus) lastInBar = true
                    },
            )
        }

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
