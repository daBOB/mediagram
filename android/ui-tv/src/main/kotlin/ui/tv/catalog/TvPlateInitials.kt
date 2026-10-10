package ui.tv.catalog

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.tv.material3.MaterialTheme
import catalog.initialsOf
import designsystem.Spacing
import designsystem.TvType
import designsystem.webSp
import kotlin.math.roundToInt

/**
 * The letters standing in for missing artwork, sized to a share of the
 * plate's width — the phone plate's rule.
 *
 * Drawn rather than composed as a `Text` inside a `BoxWithConstraints`: the
 * size is only known at layout, and asking for it through a
 * `BoxWithConstraints` gives every plate a subcomposition of its own. A wall
 * composes a whole line of plates in the one frame that scrolls it into
 * view, and on a slow television box those subcompositions were a large
 * part of why that frame ran long. Measured here once per plate size and
 * title, in the draw phase, with no composition at all. The letters still
 * reach the plate's merged semantics, as the `Text` they replace did.
 */
@Composable
internal fun TvPlateInitials(
    title: String,
    modifier: Modifier = Modifier,
) {
    val initials = initialsOf(title)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(
        modifier =
            modifier
                .semantics { text = AnnotatedString(initials) }
                .drawWithCache {
                    val fontSize = (size.width * InitialShare).toSp()
                    val inset = Spacing.small.toPx()
                    val layout =
                        measurer.measure(
                            text = initials,
                            style =
                                TvType.titleMedium.copy(
                                    color = color,
                                    fontSize = fontSize,
                                    lineHeight = fontSize * InitialLeading,
                                    letterSpacing = InitialTracking,
                                    textAlign = TextAlign.Center,
                                ),
                            constraints = Constraints(maxWidth = (size.width - 2 * inset).roundToInt().coerceAtLeast(0)),
                        )
                    val topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f)
                    onDrawBehind { drawText(layout, topLeft = topLeft) }
                },
    )
}

/** How much of a plate's width one line of initials takes — see the phone plate's own constant. */
private const val InitialShare = 0.26f

/** The initials' line height against their size, the phone plate's. */
private const val InitialLeading = 1.1f

/** Letters standing in for artwork are set apart, the way the phone plate's are. */
private val InitialTracking = 2.webSp
