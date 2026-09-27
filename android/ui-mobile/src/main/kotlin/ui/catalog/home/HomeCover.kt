package ui.catalog.home

import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import model.MediaSet

/** The cover's own outer bounds, for a test to check what sits under it never overlaps them. */
internal const val HOME_COVER_TEST_TAG = "home-cover"

/** How long one cover story holds before advancing — the web's own `HOLD_MS` (`home-cover.js:20`). */
private const val HOLD_MS = 9_000L

/** How long one slide takes to cross-fade into the next — the web's own `cover-fade` (`home.css:18`, `1400ms`). */
internal const val CROSSFADE_MS = 1_400

/** Type set over artwork keeps the same two colours in both themes — the web's `--on-image`/`--on-image-2` (`theme.css:100-101`); shared with the pills and pager in `CoverControls.kt`. */
internal val OnImage = Color(0xFFF6F2EA)
internal val OnImage2 = Color(0xD1F6F2EA)

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
) {
    if (films.isEmpty()) return
    val context = LocalContext.current
    val reducedMotion =
        remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
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

    if (rotates) {
        LaunchedEffect(currentPage, paused, pointerDown, hasFocus) {
            if (paused || pointerDown || hasFocus) return@LaunchedEffect
            delay(HOLD_MS)
            currentPage = (currentPage + 1) % films.size
        }
    }

    // A fade in progress swallows its own taps below: both slides are
    // composed during a `Crossfade`, the incoming one on top, so without
    // this a tap 150ms into the fade — while the screen still reads as
    // almost entirely the outgoing film — would act on the film not shown.
    // Skipped on the very first composition (`firstSlide`): nothing is
    // actually fading yet then, only the one slide `Crossfade` starts on.
    var fading by remember { mutableStateOf(false) }
    var firstSlide by remember { mutableStateOf(true) }
    LaunchedEffect(currentPage) {
        if (firstSlide) {
            firstSlide = false
        } else {
            fading = true
            delay(CROSSFADE_MS.toLong())
            fading = false
        }
    }

    val windowHeight = LocalConfiguration.current.screenHeightDp.dp
    val compact = width <= CompactBreakpoint
    // A minimum, not an exact height — `CoverSlide`'s own root `Box` is
    // what enforces this floor (the `heightIn(min=)` + `matchParentSize()`
    // pattern `HomeFeatures.kt` already uses, see its own note); neither
    // this `BoxWithConstraints` nor `Crossfade` below carry a size modifier
    // of their own, so both just wrap whatever height `CoverSlide` resolves
    // to. Nothing here ever asks a child to `fillMaxSize()` under this
    // `LazyColumn` item's own unbounded height — the trap that once
    // collapsed a `HorizontalPager` here — and a short window (split
    // screen) can grow the text past the floor instead of clipping it.
    val coverMinHeight =
        if (compact) (windowHeight.value * 0.84f).dp else fluid(620f, 0.635f, 705f, windowHeight.value).dp

    val page = currentPage.coerceIn(0, films.lastIndex)

    BoxWithConstraints(
        modifier =
            Modifier
                .testTag(HOME_COVER_TEST_TAG)
                .fillMaxWidth()
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
        val columnWidth = maxWidth
        // A cross-fade, not `HorizontalPager`'s own slide-past — the web's
        // own cover turns by fading (`.cover-slide{animation:cover-fade}`,
        // `home.css:18`), never sliding; a fade also never leaves a title
        // clipped at a page edge mid-turn the way a slide briefly can.
        //
        // Keyed on the film itself (`MediaSet`, a data class), not an
        // index: the outgoing slide during a fade still reads `films[shown]`
        // through this same lambda, and if the lineup has shrunk since the
        // fade began (a film pinned elsewhere, marked watched, or dropped
        // by a library refresh), the old index can be past the new list's
        // end. A `MediaSet` value stays valid to draw regardless of what the
        // list it came from looks like now.
        Crossfade(targetState = films[page], animationSpec = tween(CROSSFADE_MS), label = "cover") { set ->
            CoverSlide(
                set = set,
                width = width,
                columnWidth = columnWidth,
                compact = compact,
                minHeight = coverMinHeight,
                watchlisted = set.setId in watchlist,
                onPlay = { onPlay(set) },
                onDetails = { onOpenTitle(set.setId) },
                onToggleWatchlist = { listed -> onToggleWatchlist(set.setId, listed) },
            )
        }
        if (fading) {
            // Consumes every tap without doing anything with it — no
            // ripple, no callback — so a press mid-fade lands on nothing
            // rather than on whichever slide happens to be on top.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            )
        }
        if (films.size > 1) {
            CoverPager(
                count = films.size,
                current = page,
                rotates = rotates,
                paused = paused,
                onTogglePause = { paused = !paused },
                onPick = { index -> currentPage = index },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = gutterFor(width), bottom = if (compact) 40.dp else 18.dp),
            )
        }
    }
}
