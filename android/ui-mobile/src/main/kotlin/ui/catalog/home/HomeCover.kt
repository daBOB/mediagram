package ui.catalog.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.LocalReducedMotion
import kotlinx.coroutines.delay
import model.MediaSet
import ui.chrome.IndicationInk

/** The cover's own outer bounds, for a test to check what sits under it never overlaps them. */
internal const val HOME_COVER_TEST_TAG = "home-cover"

/** How long one cover story holds before advancing — the web's own `HOLD_MS` (`home-cover.js:20`). */
private const val HOLD_MS = 9_000L

/** How long a slide takes to come up out of the ground — the web's own `cover-fade` (`home.css:18`, `1400ms`). */
internal const val COVER_FADE_MS = 1_400

/**
 * The cover story: one film at a time, full bleed under the chrome, its
 * backdrop behind a headline set at a magazine cover's own size — a Compose
 * port of `home-cover.js`.
 *
 * [width] is the window's own width, not this composable's own column (a
 * `BoxWithConstraints` below reads that for itself) — the web's clamps are
 * all `vw`-relative, the same value the departments bar's own gutter reads.
 */
@Composable
internal fun HomeCover(
    films: List<MediaSet>,
    watchlist: Set<String>,
    width: Dp,
    onPlay: (MediaSet) -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatchlist: (String, Boolean) -> Unit,
    // Whether the page has already played the cover's arrival. Held by the
    // page, never saved: a lazy list recycling the cover's slot, or a sync,
    // must not replay it, while a new visit does (`catalog.css:136`).
    arrived: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    if (films.isEmpty()) return
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    // The web holds rotation on pointerenter/focusin (`home-cover.js:77-80`);
    // a TalkBack/switch-access user relies on touch exploration instead of a
    // resting finger, so rotation never starts for one at all.
    val touchExplorationEnabled =
        remember {
            (context.getSystemService(AccessibilityManager::class.java))?.isTouchExplorationEnabled == true
        }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var pointerDown by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val rotates = films.size > 1 && !reducedMotion && !touchExplorationEnabled
    val page = currentPage.coerceIn(0, films.lastIndex)

    // Which film the last turn brought in, by the timer or a dot. Only it and
    // the film the page first arrived on come up out of the ground and settle;
    // one a refresh moves into view, its predecessor gone from the lineup, is
    // simply there, as on the web's settled redraw (`catalog.css:136`). Counted
    // from the page shown, so a shrunk lineup never "turns" to the film it
    // shows, and dropped once the shown film stops being it.
    var turnedTo by remember { mutableStateOf<String?>(null) }
    val turn = { next: Int ->
        if (next != page) {
            turnedTo = films[next].setId
            currentPage = next
        }
    }

    if (rotates) {
        LaunchedEffect(page, paused, pointerDown, hasFocus, films) {
            if (paused || pointerDown || hasFocus) return@LaunchedEffect
            delay(HOLD_MS)
            turn((page + 1) % films.size)
        }
    }

    // A turn swallows taps while its film is still coming up out of the dark,
    // so a press lands on nothing rather than on a control the viewer can
    // barely see. The first film comes up as the page arrives and takes taps,
    // as any arriving page does; under reduced motion nothing fades at all.
    var fading by remember { mutableStateOf(false) }
    LaunchedEffect(films[page].setId) { if (turnedTo != films[page].setId) turnedTo = null }
    // A timer guarding taps, not motion, so it is not a tween delay.
    LaunchedEffect(turnedTo) {
        fading = false
        if (turnedTo == null || reducedMotion) return@LaunchedEffect
        fading = true
        delay(COVER_FADE_MS.toLong())
        fading = false
    }

    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    val compact = width <= CompactBreakpoint
    // A minimum, not an exact height — `CoverSlide`'s own root `Box` is
    // what enforces this floor (the `heightIn(min=)` + `matchParentSize()`
    // pattern `HomeFeatures.kt` already uses, see its own note); neither
    // this `BoxWithConstraints` nor the keyed slide below carry a size
    // modifier of their own, so both just wrap whatever height `CoverSlide`
    // resolves to. Nothing here ever asks a child to `fillMaxSize()` under
    // this `LazyColumn` item's own unbounded height — the trap that once
    // collapsed a `HorizontalPager` here — and a short window (split
    // screen) can grow the text past the floor instead of clipping it.
    val coverMinHeight = coverHeightFor(compact, windowHeight)

    BoxWithConstraints(
        modifier =
            Modifier
                .testTag(HOME_COVER_TEST_TAG)
                .fillMaxWidth()
                .background(CoverGround)
                .focusGroup()
                .onFocusChanged { hasFocus = it.hasFocus }
                // `Initial`, not `Main`: sees every down/up even when a
                // pill or "Details" consumes its own tap, since any finger
                // resting on the cover holds the rotation, not just one on
                // empty space.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        pointerDown = true
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        pointerDown = false
                    }
                },
    ) {
        IndicationInk(OnImage) {
            val columnWidth = maxWidth
            val set = films[page]
            // The old slide goes at once and the new one comes up out of the black
            // ground (`home.css:15,18`, `home-cover.js:62`), so two films never
            // share the screen. Keyed by id, so a refresh handing over the same
            // film with new details redraws it in place rather than turning.
            key(set.setId) {
                // Decided once per slide, so the flag flipping mid-fade doesn't
                // strip the modifiers from the slide that set it.
                val fromArrival = remember { turnedTo == null && !arrived.value }
                val turning = remember { fromArrival || turnedTo == set.setId }
                CoverSlide(
                    set = set,
                    width = width,
                    columnWidth = columnWidth,
                    compact = compact,
                    minHeight = coverMinHeight,
                    watchlisted = set.setId in watchlist,
                    turning = turning,
                    arrived = if (fromArrival) arrived else null,
                    onPlay = { onPlay(set) },
                    onDetails = { onOpenTitle(set.setId) },
                    onToggleWatchlist = { listed -> onToggleWatchlist(set.setId, listed) },
                )
            }
            if (fading && set.setId == turnedTo) CoverTapShield()
            if (films.size > 1) {
                CoverPager(
                    count = films.size,
                    current = page,
                    rotates = rotates,
                    paused = paused,
                    onTogglePause = { paused = !paused },
                    onPick = turn,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = gutterFor(width), bottom = if (compact) 40.dp else 18.dp),
                )
            }
        }
    }
}
