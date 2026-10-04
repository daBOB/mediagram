package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import designsystem.Overscan

/**
 * A page opened from the catalogue — a title, a show or course, a season,
 * a list — whose scrolling leaves its heading where it is while the remote
 * rests on something already in plain view.
 *
 * A television's own rule moves whatever takes focus to about a third of
 * the way down the screen, wherever it already was. That suits a wall
 * scrolled row by row, but a page lands the remote on its first stop the
 * moment it opens, and that rule then scrolls the page's own name up into
 * the overscan edge before a viewer has pressed anything. So a stop inside
 * the overscan-safe band moves nothing here; one outside it, or only partly
 * inside, still gets the television's rule — which, near the top of a page,
 * brings the page back to its top.
 *
 * [takesArrivalFocus] is off for a page coming back to a stop its wall or
 * rows cannot see — see [LocalTakesArrivalFocus].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvPage(
    takesArrivalFocus: Boolean = true,
    content: @Composable () -> Unit,
) {
    val platform = LocalBringIntoViewSpec.current
    val inset = with(LocalDensity.current) { Overscan.vertical.toPx() }
    val spec = remember(platform, inset) { StaysPutWhenOnScreen(platform, inset) }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides spec,
        LocalTakesArrivalFocus provides takesArrivalFocus,
        content = content,
    )
}

/** [TvPage]'s rule: nothing to scroll while the target sits inside the [inset] band; otherwise whatever [fallback] says. */
@OptIn(ExperimentalFoundationApi::class)
internal class StaysPutWhenOnScreen(
    private val fallback: BringIntoViewSpec,
    private val inset: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        // Half a pixel either way: the first stop of a lazy page sits
        // exactly on its content padding, which is this same inset.
        val onScreen = offset >= inset - Slack && offset + size <= containerSize - inset + Slack
        return if (onScreen) 0f else fallback.calculateScrollDistance(offset, size, containerSize)
    }
}

private const val Slack = 0.5f

/**
 * Anything inside that is brought into view — the remote landing on it —
 * brings everything above it in this block along too, so the top of the
 * block shows with it whenever there is room.
 *
 * A title page's Play sits below the title, beside the art: the remote
 * coming back up from a long overview would otherwise scroll just far
 * enough to show Play and leave the title off the top of the screen, with
 * no stop above Play to bring it back.
 */
internal fun Modifier.revealsFromTop(): Modifier = this then RevealsFromTop

private data object RevealsFromTop : ModifierNodeElement<RevealsFromTopNode>() {
    override fun create() = RevealsFromTopNode()

    override fun update(node: RevealsFromTopNode) = Unit
}

private class RevealsFromTopNode :
    Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        val block = requireLayoutCoordinates()
        bringIntoView {
            val child = boundsProvider() ?: return@bringIntoView null
            if (!childCoordinates.isAttached || !block.isAttached) return@bringIntoView null
            val at = block.localPositionOf(childCoordinates, child.topLeft)
            Rect(left = at.x, top = 0f, right = at.x + child.width, bottom = at.y + child.height)
        }
    }
}


/**
 * Brought into view, this block — a title page's tab row — asks for the page
 * from its own top down to the foot of the safe band, a screen's worth less
 * the bottom inset. Every television rule settles a request that size the
 * same way, flush with the bottom inset, which leaves the tab row at the
 * top one: the panel it heads then shows whole beneath it.
 *
 * A panel of facts has nothing in it a remote can rest on, and a remote
 * cannot scroll a page it has no stop in — without this, reaching the tabs
 * from the spread above would leave Details or About peeking a few lines
 * under the fold, out of reach. [viewport] is the page's own height in
 * pixels, read when asked, since a request is made long after composition.
 */
internal fun Modifier.revealsPageBelow(viewport: () -> Int): Modifier = this then RevealsPageBelow(viewport)

private data class RevealsPageBelow(
    val viewport: () -> Int,
) : ModifierNodeElement<RevealsPageBelowNode>() {
    override fun create() = RevealsPageBelowNode(viewport)

    override fun update(node: RevealsPageBelowNode) {
        node.viewport = viewport
    }
}

private class RevealsPageBelowNode(
    var viewport: () -> Int,
) : Modifier.Node(),
    BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        val block = requireLayoutCoordinates()
        val inset = with(requireDensity()) { Overscan.vertical.toPx() }
        bringIntoView {
            val child = boundsProvider() ?: return@bringIntoView null
            if (!childCoordinates.isAttached || !block.isAttached) return@bringIntoView null
            val at = block.localPositionOf(childCoordinates, child.topLeft)
            val height = viewport() - inset
            if (height <= 0f) return@bringIntoView Rect(at, child.size)
            Rect(left = at.x, top = 0f, right = at.x + child.width, bottom = height)
        }
    }
}
