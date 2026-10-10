package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What [catalogueIndication] draws in: the tint's ink, the focus ring's accent. */
data class IndicationTone(val ink: Color, val ring: Color)

/** Provided by the theme; the player provides white ink over video. */
val LocalIndicationTone = staticCompositionLocalOf { IndicationTone(ink = Palette.Text, ring = Accent.Default.resolve(dark = true)) }

/** The web's hover fills sit at 8–10% of the ink (`player-card.css:79-82`). */
private const val INK_ALPHA = 0.08f
private val RING = 2.dp

/**
 * The web's feedback in place of Material's ripple, which the web turns off
 * (`theme.css:164`, no tap highlight):
 * - a quiet ink tint while pressed, hovered or focused, fading on `--hover`;
 * - a 2dp accent ring on focus, at once (`theme.css:170`). Only a keyboard or
 *   D-pad gives a clickable focus, so this is the web's `:focus-visible`;
 * - a snapped shrink to [pressScale] while pressed (`theme.css:167`,
 *   `button:active`). Links on the web don't shrink, so link-type sites
 *   pass 1.
 */
fun catalogueIndication(pressScale: Float = 0.98f): IndicationNodeFactory = CatalogueIndication(pressScale)

private data class CatalogueIndication(val pressScale: Float) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = CatalogueIndicationNode(interactionSource, pressScale)
}

private class CatalogueIndicationNode(
    private val source: InteractionSource,
    private val pressScale: Float,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val tint = Animatable(0f)
    private val presses = mutableSetOf<PressInteraction.Press>()
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val focuses = mutableSetOf<FocusInteraction.Focus>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                    is HoverInteraction.Enter -> hovers += interaction
                    is HoverInteraction.Exit -> hovers -= interaction.enter
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                }
                val lit = presses.isNotEmpty() || hovers.isNotEmpty() || focuses.isNotEmpty()
                launch { tint.animateTo(if (lit) INK_ALPHA else 0f, Motion.hover()) }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val tone = currentValueOf(LocalIndicationTone)
        val shrink = presses.isNotEmpty() && pressScale != 1f
        if (shrink) {
            scale(pressScale) {
                this@draw.drawContent()
                drawRect(tone.ink.copy(alpha = tint.value))
            }
        } else {
            drawContent()
            drawRect(tone.ink.copy(alpha = tint.value))
        }
        // ponytail: drawn on the bounds, not 4dp outside them as the web's
        // outline-offset is; a clipped control would cut an outside ring off.
        if (focuses.isNotEmpty()) drawRect(tone.ring, style = Stroke(RING.toPx()))
    }
}
