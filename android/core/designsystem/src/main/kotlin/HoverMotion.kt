package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The web's hover motion: artwork lifts or zooms, arrows nudge toward where
 * they lead (`catalog.css:44-51`, `home.css:157-160,208-210`,
 * `title-page.css:26-27,84-86,178-180`). Controls never lift or grow, so
 * this goes on artwork and arrow glyphs only.
 *
 * It follows a mouse, trackpad or hovering stylus ([HoverInteraction]), the
 * web's `(hover: hover)` gate. A finger never lifts or zooms artwork; a
 * zoom still running after the tap navigated would be motion for nobody.
 * An arrow ([nudgeOnPress]) also nudges while pressed: a press is the touch
 * form of a hover, and the nudge is a control-level cue.
 */
fun Modifier.hoverMotion(
    source: InteractionSource,
    durationMs: Int,
    lift: Dp = 0.dp,
    nudge: Dp = 0.dp,
    zoom: Float = 1f,
    nudgeOnPress: Boolean = nudge != 0.dp,
): Modifier = this then HoverMotionElement(source, durationMs, lift, nudge, zoom, nudgeOnPress)

private data class HoverMotionElement(
    val source: InteractionSource,
    val durationMs: Int,
    val lift: Dp,
    val nudge: Dp,
    val zoom: Float,
    val nudgeOnPress: Boolean,
) : ModifierNodeElement<HoverMotionNode>() {
    override fun create() = HoverMotionNode(this)

    override fun update(node: HoverMotionNode) {
        val sourceChanged = node.spec.source != source
        node.spec = this
        if (sourceChanged) node.listen()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "hoverMotion"
    }
}

private class HoverMotionNode(var spec: HoverMotionElement) : Modifier.Node(), LayoutModifierNode {
    private val progress = Animatable(0f)
    private val hovers = mutableSetOf<HoverInteraction.Enter>()
    private val presses = mutableSetOf<PressInteraction.Press>()
    private var listening: Job? = null

    override fun onAttach() = listen()

    fun listen() {
        if (!isAttached) return
        listening?.cancel()
        hovers.clear()
        presses.clear()
        listening =
            coroutineScope.launch {
                spec.source.interactions.collect { interaction ->
                    when (interaction) {
                        is HoverInteraction.Enter -> hovers += interaction
                        is HoverInteraction.Exit -> hovers -= interaction.enter
                        is PressInteraction.Press -> presses += interaction
                        is PressInteraction.Release -> presses -= interaction.press
                        is PressInteraction.Cancel -> presses -= interaction.press
                    }
                    val on = hovers.isNotEmpty() || (spec.nudgeOnPress && presses.isNotEmpty())
                    launch { progress.animateTo(if (on) 1f else 0f, tween(spec.durationMs, easing = Motion.Ease)) }
                }
            }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val p = progress.value
                translationY = spec.lift.toPx() * p
                translationX = spec.nudge.toPx() * p
                val scale = 1f + (spec.zoom - 1f) * p
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
