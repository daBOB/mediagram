package ui.common.ambient

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** How far past the art the glow takes to come back to full strength. */
val GlowAwayReach = 96.dp

/**
 * Keeps Ambient's glow off a backdrop: the glow behind the page ([AmbientBackdrop])
 * leaves out the art's own bounds and fades back in over [GlowAwayReach] around
 * them, so it never shows over the picture and never meets the art's faded box
 * at a hard edge. Cut from the glow itself, which spans the window, so no
 * page's clip can cut the fade short. Nothing without a [tint] (Ambient off).
 */
fun Modifier.keepsGlowAway(tint: AmbientTint?): Modifier = if (tint == null) this else this then KeepsGlowAwayElement(tint)

private data class KeepsGlowAwayElement(
    val tint: AmbientTint,
) : ModifierNodeElement<KeepsGlowAwayNode>() {
    override fun create() = KeepsGlowAwayNode(tint)

    override fun update(node: KeepsGlowAwayNode) {
        node.tint.arts.remove(node)
        node.tint = tint
    }
}

private class KeepsGlowAwayNode(
    var tint: AmbientTint,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode {
    // Unclipped: a backdrop part-scrolled under a bar still keeps the glow off where it runs on.
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        tint.arts[this] = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
    }

    override fun onDetach() {
        tint.arts.remove(this)
    }
}

/** Takes the glow away inside [art] and fades it back in over [reach] around it; the layer must be offscreen. */
internal fun DrawScope.cutGlowAround(
    art: Rect,
    reach: Float,
) {
    val cut = BlendMode.DstOut
    // Eased rather than straight, so the glow's return has no visible start to its ramp.
    val fromArt =
        arrayOf(0f to Color.Black, 0.3f to Color.Black.copy(alpha = 0.78f), 0.65f to Color.Black.copy(alpha = 0.28f), 1f to Color.Transparent)
    val (l, t, r, b) = art
    drawRect(Color.Black, art.topLeft, art.size, blendMode = cut)
    drawRect(Brush.horizontalGradient(*fromArt, startX = l, endX = l - reach), Offset(l - reach, t), Size(reach, art.height), blendMode = cut)
    drawRect(Brush.horizontalGradient(*fromArt, startX = r, endX = r + reach), Offset(r, t), Size(reach, art.height), blendMode = cut)
    drawRect(Brush.verticalGradient(*fromArt, startY = t, endY = t - reach), Offset(l, t - reach), Size(art.width, reach), blendMode = cut)
    drawRect(Brush.verticalGradient(*fromArt, startY = b, endY = b + reach), Offset(l, b), Size(art.width, reach), blendMode = cut)
    for (corner in listOf(art.topLeft, art.topRight, art.bottomLeft, art.bottomRight)) {
        val x = if (corner.x == l) l - reach else r
        val y = if (corner.y == t) t - reach else b
        drawRect(Brush.radialGradient(*fromArt, center = corner, radius = reach), Offset(x, y), Size(reach, reach), blendMode = cut)
    }
}
