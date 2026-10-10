package ui.tv.catalog

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How a page moves when the remote comes into it: 420ms on the web's `--ease`,
 * `cubic-bezier(0.32, 0.72, 0, 1)` (`theme.css:120`). The web scripts no
 * scroll for this — a browser scrolls its own page — so the pace is the one
 * it gives a page's content rising into place under the masthead,
 * `page-in 420ms var(--ease)` (`shell.css:162`).
 */
internal val TvEntryGlide: AnimationSpec<Float> = tween(durationMillis = 420, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))

/**
 * A page's vertical list whose scroll glides, over [TvEntryGlide], to the
 * stop the remote comes into it on — Down off the bar onto the first row a
 * hero leaves at the screen's foot, above all — ending where the list's own
 * scroll [rule] rests that stop. The list and everything inside it read
 * [rule] wrapped, so a caller with its own passes it here rather than
 * providing it around the call.
 *
 * Left to Compose, a newly focused stop scrolls in on a short spring that
 * asks the rule again every frame: the hero went in about five frames, and
 * [StaysPutWhenOnScreen] stopped it dead the frame the row crossed into the
 * safe band. While the glide runs the list's own scroll-into-view stands
 * aside — it answers nothing to scroll — and the remote moving on ends the
 * glide, so the next stop scrolls in the ordinary way.
 *
 * [content] puts the modifier it is handed on the list itself. Under reduced
 * motion Android scales animations to nothing, and the glide lands at once.
 */
@Composable
internal fun GlidesOnEntry(
    state: ScrollableState,
    rule: BringIntoViewSpec = LocalBringIntoViewSpec.current,
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val glide = remember(rule) { EntryGlide(rule) }
    val modifier =
        Modifier
            .onPlaced { glide.list = it }
            .onFocusChanged { glide.onFocus(it.hasFocus) }
            .onFocusedBoundsChanged { glide.onStop(it, scope, state) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides glide) { content(modifier) }
}

private class EntryGlide(
    private val rule: BringIntoViewSpec,
) : BringIntoViewSpec {
    var list: LayoutCoordinates? = null
    private var inside = false
    private var arriving = false
    private var running: Job? = null

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = if (running?.isActive == true) 0f else rule.calculateScrollDistance(offset, size, containerSize)

    // Heard before the stop's bounds are: the list takes focus, then the stop inside it reports where it is.
    fun onFocus(hasFocus: Boolean) {
        arriving = hasFocus && (arriving || !inside)
        inside = hasFocus
    }

    fun onStop(
        stop: LayoutCoordinates?,
        scope: CoroutineScope,
        state: ScrollableState,
    ) {
        if (stop == null) {
            running?.cancel()
            return
        }
        // A stop that reports no bounds (a wall header handing the entry on) leaves this for the one it hands to.
        if (!arriving) return
        arriving = false
        val list = list?.takeIf { it.isAttached && stop.isAttached } ?: return
        val at = list.localBoundingBoxOf(stop, clipBounds = false)
        val distance = rule.restingDistance(at.top, at.height, list.size.height.toFloat())
        if (distance != 0f) running = scope.launch { state.animateScrollBy(distance, TvEntryGlide) }
    }
}

/**
 * How far Compose's own scroll-into-view carries a stop at [top] before it
 * rests: it asks the rule again every frame and stops the frame it answers
 * nothing, so a rule with a safe band ([StaysPutWhenOnScreen]) rests at the
 * band's edge, short of the spot its first answer named — where every other
 * move on that page settles too. Found by halving, since a rule only says how
 * far to go from where a stop is.
 */
private fun BringIntoViewSpec.restingDistance(
    top: Float,
    size: Float,
    container: Float,
): Float {
    var short = 0f
    var far = calculateScrollDistance(top, size, container)
    if (far == 0f) return 0f
    repeat(20) {
        val mid = (short + far) / 2
        if (calculateScrollDistance(top - mid, size, container) == 0f) far = mid else short = mid
    }
    return far
}
