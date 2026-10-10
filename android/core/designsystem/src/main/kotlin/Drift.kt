package designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A one-shot slow scale (and pan) from [from] to [to], the web's cover
 * zoom (`home.css:27,128`) and Featured poster drift (`playback.css:499-503`).
 * [translate] is a fraction of the size, as CSS `translate(-1.5%, -1%)` is.
 *
 * Paused ([running] false) it holds where it is and resumes from there on
 * the same curve, as CSS `animation-play-state: paused` does: the clock runs
 * linear and [easing] is applied when drawn. Under reduced motion it shows
 * [restScale] and never ticks, which is the web's `animation: none` (for the
 * reel that rest is 1, not the drift's end).
 */
fun Modifier.drift(
    from: Float,
    to: Float,
    durationMs: Int,
    easing: Easing,
    running: Boolean = true,
    translate: Offset = Offset.Zero,
    restScale: Float = to,
): Modifier = this then DriftElement(from, to, durationMs, easing, running, translate, restScale)

private data class DriftElement(
    val from: Float,
    val to: Float,
    val durationMs: Int,
    val easing: Easing,
    val running: Boolean,
    val translate: Offset,
    val restScale: Float,
) : ModifierNodeElement<DriftNode>() {
    override fun create() = DriftNode(this)

    override fun update(node: DriftNode) {
        node.spec = this
        node.sync()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drift"
    }
}

private class DriftNode(var spec: DriftElement) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    private val clock = Animatable(0f)
    private var job: Job? = null
    private var still = false

    override fun onAttach() {
        still = currentValueOf(LocalReducedMotion)
        sync()
    }

    fun sync() {
        if (!isAttached || still) return
        if (!spec.running) {
            job?.cancel()
            job = null
        } else if (job?.isActive != true && clock.value < 1f) {
            val left = ((1f - clock.value) * spec.durationMs).roundToInt()
            job = coroutineScope.launch { clock.animateTo(1f, tween(left, easing = LinearEasing)) }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                if (still) {
                    scaleX = spec.restScale
                    scaleY = spec.restScale
                } else {
                    val eased = spec.easing.transform(clock.value)
                    val scale = spec.from + (spec.to - spec.from) * eased
                    scaleX = scale
                    scaleY = scale
                    translationX = spec.translate.x * size.width * eased
                    translationY = spec.translate.y * size.height * eased
                }
            }
        }
    }
}
