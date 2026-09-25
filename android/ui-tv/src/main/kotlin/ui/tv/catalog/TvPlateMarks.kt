package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale

/** The progress rule's own tag, so a test can tell it apart from the tick. */
internal const val TvPlateProgressTag = "tv-plate-progress"

/** The watched tick's own tag, for the same reason [TvPlateProgressTag] carries one. */
internal const val TvPlateWatchedTickTag = "tv-plate-watched-tick"

/**
 * The progress rule along the foot of a plate. Not tv-material's own — it
 * ships no progress indicator ([ui.tv.setup.TvLoadingIndicator] hits the
 * same wall for its spinner) — so this is the plain fractional-width box
 * that stands in for one, drawn in the catalogue's one accent. [modifier]
 * carries the `align` the caller's `BoxWithConstraints` scope alone can
 * grant — a scoped modifier only resolves inside the scope that produced it,
 * not in a composable split out from it.
 */
@Composable
internal fun TvProgressRule(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag(TvPlateProgressTag).fillMaxWidth().height(ProgressHeight).background(Palette.Rule),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(fraction = fraction.coerceIn(0f, 1f))
                    .background(Palette.Imprint),
        )
    }
}

/**
 * The tick a finished title draws — see [TvPlate]'s own note on why it is
 * a mark of its own rather than a full progress rule. Ported from the
 * phone plate's own mark, in the same corner. [modifier] carries `align` for
 * the same reason [TvProgressRule]'s does.
 */
@Composable
internal fun TvWatchedTick(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .testTag(TvPlateWatchedTickTag)
                .padding(Spacing.small)
                .size(TickSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "✓", style = TvTypeScale.body, color = MaterialTheme.colorScheme.onPrimary)
    }
}

private val TickSize = 28.dp
private val ProgressHeight = 4.dp
