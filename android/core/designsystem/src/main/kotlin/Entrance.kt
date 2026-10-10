package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A one-shot entrance from (alpha 0, [fromY] below, [fromScale]) to rest,
 * the shape of every web entrance: the page turn (`shell.css:161-163`), the
 * scroll reveal (`theme.css:206-209`), the art-in (`title-page.css:63`), the
 * menu (`title-page.css:46`). Position and scale run [durationMs], the fade
 * [fadeMs]; the web's reveal fades faster than it rises.
 *
 * It starts when first **placed**, not composed, so a lazy list composing an
 * item ahead of the viewport doesn't play it off screen. [delayMs] is part of
 * the tween, so "Remove animations" zeroes it with everything else. With
 * [seen] already true it starts at rest: the web's rule that a redraw or a
 * return never replays an entrance (`catalog.css:136`, `.settled`).
 *
 * [seen] is the same state for the same item from one composition to the
 * next. A different one means a different item, which a lazy list can hand
 * this node to, and the entrance starts over for it.
 *
 * Under reduced motion it is at rest from its first frame, as the web draws
 * the page at once: zeroed tweens alone would still finish a frame late.
 */
fun Modifier.entrance(
    durationMs: Int,
    delayMs: Int = 0,
    fromY: Dp = 0.dp,
    fromScale: Float = 1f,
    fadeMs: Int = durationMs,
    easing: Easing = Motion.Ease,
    seen: MutableState<Boolean>? = null,
): Modifier = this then EntranceElement(durationMs, delayMs, fromY, fromScale, fadeMs, easing, seen)

private data class EntranceElement(
    val durationMs: Int,
    val delayMs: Int,
    val fromY: Dp,
    val fromScale: Float,
    val fadeMs: Int,
    val easing: Easing,
    val seen: MutableState<Boolean>?,
) : ModifierNodeElement<EntranceNode>() {
    override fun create() = EntranceNode(this)

    override fun update(node: EntranceNode) {
        node.spec = this
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "entrance"
    }
}

private class EntranceNode(spec: EntranceElement) :
    Modifier.Node(),
    LayoutModifierNode,
    LayoutAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var fade = Animatable(0f)
    private var move = Animatable(0f)
    private var started = false
    private var placed = false

    var spec: EntranceElement = spec
        set(value) {
            val anotherItem = value.seen !== field.seen
            field = value
            if (anotherItem) {
                startOver()
                // Handed over after this node was already placed for the new
                // item, so no placement is left to start it.
                if (placed && isAttached) play()
            }
        }

    init {
        startOver()
    }

    /** From the start, or straight at rest where [EntranceElement.seen] says this item has played. */
    private fun startOver() {
        started = spec.seen?.value == true
        val from = if (started) 1f else 0f
        fade = Animatable(from)
        move = Animatable(from)
        // The layer block holds the animatables it last read, not these.
        if (isAttached) invalidatePlacement()
    }

    private fun play(fromMeasure: Boolean = false) {
        started = true
        spec.seen?.value = true
        // Read at each start, so the setting turned on or off reaches the next entrance.
        if (currentValueOf(LocalReducedMotion)) {
            toRest()
            // Started from onPlaced or a reused node's new spec, the layer block
            // still holds the animatables it last read; from measure, the block
            // about to be built reads these.
            if (!fromMeasure && isAttached) invalidatePlacement()
            return
        }
        coroutineScope.launch { fade.animateTo(1f, tween(spec.fadeMs, spec.delayMs, spec.easing)) }
        coroutineScope.launch { move.animateTo(1f, tween(spec.durationMs, spec.delayMs, spec.easing)) }
    }

    // A lazy list parks an item scrolled out of view and hands its nodes
    // back when it returns, with the animation cancelled wherever it had got
    // to. Placed once, it has played: finish it, as a revealed web section
    // stays in, rather than leave it half-faded.
    override fun onReset() {
        placed = false
        if (!started) return
        toRest()
        if (isAttached) invalidatePlacement()
    }

    private fun toRest() {
        fade = Animatable(1f)
        move = Animatable(1f)
    }

    override fun onDetach() {
        placed = false
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = true
        if (!started) play()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // With nothing to play, start before this placement draws: onPlaced comes after it.
        if (!started && currentValueOf(LocalReducedMotion)) play(fromMeasure = true)
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                alpha = fade.value
                val moved = move.value
                translationY = spec.fromY.toPx() * (1f - moved)
                val scale = spec.fromScale + (1f - spec.fromScale) * moved
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
