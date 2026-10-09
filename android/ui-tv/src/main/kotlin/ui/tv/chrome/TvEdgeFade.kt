package ui.tv.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing

/** Which ends of a sideways-scrolled row have something clipped past them, and so are drawn fading out. */
internal data class EdgeFades(
    val start: Boolean,
    val end: Boolean,
)

/** [EdgeFades] as the row reports it, so a test can read what the bar decided rather than its pixels. */
internal val EdgeFadesKey = SemanticsPropertyKey<EdgeFades>("EdgeFades")

/** How far in from a clipped end the row fades: past a focused pill's ring room, short of a whole word. */
internal val EdgeFadeWidth = Spacing.large

/**
 * Fades whichever end [fades] names to nothing — a mask over the row's own
 * pixels rather than a scrim in the bar's colour, since over a cover the bar
 * is translucent and a scrim would tint the art behind it. Reports [fades]
 * as [EdgeFadesKey]. Placed before the scroll in the chain, so it masks the
 * clipped viewport rather than the content scrolling inside it. Offscreen
 * only while an end fades: the mask needs its own buffer, a row that fits
 * does not, and the box redraws the bar on every frame of a cover's blend.
 */
internal fun Modifier.fadeClippedEdges(fades: EdgeFades): Modifier =
    semantics { this[EdgeFadesKey] = fades }
        .graphicsLayer { compositingStrategy = if (fades.start || fades.end) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val width = EdgeFadeWidth.toPx().coerceAtMost(size.width / 2)
            if (fades.start) {
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, endX = width), size = Size(width, size.height), blendMode = BlendMode.DstIn)
            }
            if (fades.end) {
                val from = size.width - width
                drawRect(
                    Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = from, endX = size.width),
                    topLeft = Offset(from, 0f),
                    size = Size(width, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
