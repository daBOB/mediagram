package ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Motion
import designsystem.Radius

/** Each band of the ring, accent then ground: `0 0 0 2px paper, 0 0 0 4px accent` (`appearance.css:47`). */
private val RING_BAND = 2.dp

/** The unchosen edge, `inset 0 0 0 1px var(--rule)` (`appearance.css:45`). */
private val HAIRLINE = 1.dp

/**
 * A picture of a theme or an artwork mode: 16:9, at [Radius.card], ringed
 * in the accent, with the page's own ground as the gap between the ring and
 * the picture, when selected. Purely presentational: the caller owns
 * `selectable` and the merge boundary, the same way an accent dot's own
 * wrapping column does; putting `selectable` on this nested box instead left
 * it out of the caller's merged semantics node entirely.
 */
@Composable
internal fun SwatchCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(Radius.card)
    val colors = MaterialTheme.colorScheme
    // `.swatch { transition: box-shadow var(--hover) }` (`appearance.css:45,47`).
    val shown = animateFloatAsState(if (selected) 1f else 0f, animationSpec = Motion.hover(), label = "swatch ring")
    Box(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .selectionRing(shown, ring = colors.primary, ground = colors.background, radius = Radius.card, hairline = colors.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(shape), content = content)
    }
}

/**
 * The ring Settings draws on a chosen swatch or accent dot: [ring] at the
 * edge, then a band of the page's [ground], then the picture. [shown] runs
 * 0 to 1 as it fades in, and it is read here, in the draw phase. Drawn over
 * the picture's edge rather than by insetting it, so choosing one never lays
 * anything out again. [hairline] is the unchosen edge, fading out as the
 * ring fades in.
 */
internal fun Modifier.selectionRing(
    shown: State<Float>,
    ring: Color,
    ground: Color,
    radius: Dp,
    hairline: Color = Color.Transparent,
): Modifier =
    drawWithContent {
        drawContent()
        val p = shown.value
        val band = RING_BAND.toPx()
        val hair = HAIRLINE.toPx()
        val corner = radius.toPx()
        roundBand(hairline.copy(alpha = hairline.alpha * (1f - p)), hair / 2, hair, corner)
        if (p > 0f) {
            roundBand(ground.copy(alpha = ground.alpha * p), band + band / 2, band, corner)
            roundBand(ring.copy(alpha = ring.alpha * p), band / 2, band, corner)
        }
    }

/** A rounded stroke whose middle runs [inset] in from the edge; a stroke straddles its path. */
private fun DrawScope.roundBand(color: Color, inset: Float, width: Float, corner: Float) =
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
        style = Stroke(width),
    )
