package ui.tv.catalog

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import designsystem.Overscan
import designsystem.Spacing
import ui.tv.chrome.TvDepartmentsBarHeight

/**
 * How far below the screen's own top edge a focused row's own top edge (or
 * a heading above it) is ever allowed to land while it scrolls into view —
 * the bar's full drawn height plus a little breathing room. Shared by every
 * lazy list or grid the departments bar draws over: [ui.tv.catalog.TvHome]'s
 * own vertical list and its own cover ([ui.tv.catalog.home.TvCoverSlide]),
 * [TvWall]'s own grid too, once moving a plate up or down inside it left
 * the focused one's own top behind the bar the same way an un-cleared cover
 * once did.
 */
internal val TvBarClearance = TvDepartmentsBarHeight + Overscan.vertical + Spacing.medium

/**
 * Pretends the scrollable's own leading edge sits [clearancePx] further in
 * than it really does, so the ordinary "smallest scroll that brings this
 * fully into view" rule [fallback] implements never settles a focused item
 * flush against the true edge — which is exactly where the departments bar
 * draws over whatever is there. Nothing else about how much scrolling
 * happens changes: everything not near that edge is untouched, delegated
 * straight through.
 */
internal class TvBarClearanceBringIntoViewSpec(
    private val clearancePx: Float,
    private val fallback: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = fallback.calculateScrollDistance(offset - clearancePx, size, containerSize - clearancePx)
}

/** [TvBarClearanceBringIntoViewSpec], built from the current density and the ambient default it falls back to. */
@Composable
internal fun rememberTvBarClearanceBringIntoView(clearance: Dp = TvBarClearance): BringIntoViewSpec {
    val density = LocalDensity.current
    val fallback = LocalBringIntoViewSpec.current
    return remember(density, fallback, clearance) {
        val clearancePx = with(density) { clearance.toPx() }
        TvBarClearanceBringIntoViewSpec(clearancePx, fallback)
    }
}
