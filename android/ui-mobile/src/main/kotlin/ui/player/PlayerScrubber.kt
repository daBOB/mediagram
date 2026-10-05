package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import designsystem.Spacing
import androidx.compose.ui.unit.dp
import player.clockTime

/** The least the scrub bar is drawn: under this a thumb cannot land where it means to. */
private val BAR_MIN_WIDTH = 160.dp

/**
 * The card's first row: where the film is, the scrub bar, and how long it runs
 * with when it ends — split out of [PlayerControlCard] to keep that file
 * under the project's line guideline.
 *
 * Wraps rather than squeezes: when the times would leave the bar under
 * [BAR_MIN_WIDTH] (a narrow phone, a large font) the length and end time drop
 * to their own line under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerScrubber(
    positionMs: Long,
    durationMs: Long,
    endsLabel: String,
    /** Null except mid-drag, when it holds where the thumb is rather than where the film is. */
    scrubbingTo: Float?,
    onScrubbingToChange: (Float?) -> Unit,
    onSeek: (Long) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TimeText(clockTime(positionMs))
        Slider(
            value = positionMs.toFloat(),
            onValueChange = onScrubbingToChange,
            // On release, not during: every position a thumb passes over would
            // otherwise be a seek, and every seek is a read from Telegram at a
            // fresh offset.
            onValueChangeFinished = {
                scrubbingTo?.let { onSeek(it.toLong()) }
                onScrubbingToChange(null)
            },
            // Never an empty range: a set whose length is not known yet would
            // give 0f..0f, which Slider rejects.
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0L,
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                ),
            modifier = Modifier.weight(1f).widthIn(min = BAR_MIN_WIDTH),
        )
        TimeText(listOf(clockTime(durationMs), endsLabel).filter(String::isNotEmpty).joinToString(" · "))
    }
}
