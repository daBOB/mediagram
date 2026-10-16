package ui.tv.chrome

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import designsystem.Spacing
import designsystem.tvDp

/** The bar's own ink over a hero, while [blend] is below `1f`. */
internal val TvOverCoverInk = Color(0xFFF6F2EA)

/** The bar's ground at [blend]: translucent over a hero (`0f`), the page's own ground once solid (`1f`) — at the web's 72% over a [frosted] blur. Every bar style shares it. */
@Composable
@ReadOnlyComposable
internal fun barBackground(blend: Float, frosted: Boolean = false): Color = ui.common.chrome.barBackground(MaterialTheme.colorScheme.background, blend, frosted)

/** The bar's ink at [blend], over a hero or over the page. */
@Composable
@ReadOnlyComposable
internal fun barInk(blend: Float): Color = lerp(TvOverCoverInk, MaterialTheme.colorScheme.onBackground, blend)

/** Each pill's own requester, held by the bar so its end controls can name the last pill; the selected one's is [selectedPillFocus]. */
@Composable
internal fun rememberPillRequesters(
    pills: List<String>,
    selected: Int,
    selectedPillFocus: FocusRequester,
): (Int) -> FocusRequester {
    val held = remember { HashMap<String, FocusRequester>() }
    return { index -> if (index == selected) selectedPillFocus else held.getOrPut(pills[index]) { FocusRequester() } }
}

/**
 * The bar's pills, in every style: one requester each from [requesterOf],
 * Down to [downTarget] from each (focus properties set on a container are
 * not inherited by its children), and - should they run past [modifier]'s
 * width - a scroll that fades only an end that clips something, with
 * [PillRingRoom] kept at both ends for a focused pill's grown ring. A focused
 * pill grows past its own bounds and the scroll clips at its edges, so the
 * row is padded by [PillRingRoom] all round; callers give that room back
 * where they want the pills to sit flush. A pill the row scrolls to between
 * its ends stops clear of the fade by its ring room as well.
 */
@Composable
internal fun TvPillRow(
    pills: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    ink: Color,
    downTarget: FocusRequester,
    requesterOf: (Int) -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    val downModifier = Modifier.focusProperties { down = downTarget }
    val scroll = rememberScrollState()
    val fades by remember(scroll) { derivedStateOf { EdgeFades(start = scroll.canScrollBackward, end = scroll.canScrollForward) } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRoomOnBothSides(EdgeFadeWidth + PillRingRoom)) {
        Row(
            modifier = modifier.fadeClippedEdges(fades).horizontalScroll(scroll).padding(PillRingRoom),
            // `.departments` gap 4 (`shell.css:89`).
            horizontalArrangement = Arrangement.spacedBy(4.tvDp),
        ) {
            pills.forEachIndexed { index, pill ->
                key(pill) {
                    // One requester on every pill, every recomposition: [ui.tv.rememberStableRequester]'s KDoc says why.
                    TvPill(title = pill, active = index == selected, ink = ink, onClick = { onSelect(index) }, modifier = downModifier.focusRequester(requesterOf(index)))
                }
            }
        }
    }
}

/**
 * Clear room kept at the row's ends for a focused pill's [ui.tv.TvFocus.Scale]
 * growth, 4% of its width on each side — under 8 dp for the widest pill at
 * the box's density.
 */
internal val PillRingRoom = Spacing.small

/** Brings an item into view as if it were [roomPx] wider on each side, so nothing it draws past its bounds lands on a clipped edge. */
private class RoomOnBothSidesBringIntoViewSpec(
    private val roomPx: Float,
    private val fallback: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = fallback.calculateScrollDistance(offset - roomPx, size + 2 * roomPx, containerSize)
}

@Composable
private fun rememberRoomOnBothSides(room: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    val fallback = LocalBringIntoViewSpec.current
    return remember(density, fallback, room) { RoomOnBothSidesBringIntoViewSpec(with(density) { room.toPx() }, fallback) }
}
