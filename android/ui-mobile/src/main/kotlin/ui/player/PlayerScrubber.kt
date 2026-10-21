package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import designsystem.Spacing
import androidx.compose.ui.unit.dp
import player.clockTime

/** Dim enough to read as "on its way", not as played. */
private const val BUFFERED_ALPHA = 0.4f

/** The least the scrub bar is drawn: under this a thumb cannot land where it means to. */
private val BAR_MIN_WIDTH = 160.dp

/**
 * The card's first row: where the film is, the scrub bar, and how long it runs
 * with when it ends.
 *
 * Wraps rather than squeezes: when the times would leave the bar under
 * [BAR_MIN_WIDTH] (a narrow phone, a large font) the length and end time drop
 * to their own line under it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
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
        val colors =
            SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
            )
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
            colors = colors,
            // How far the buffer reaches, as the web's bar shows it: a dimmer
            // band over the unplayed track, from the playhead on.
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    enabled = durationMs > 0L,
                    modifier =
                        Modifier.drawWithContent {
                            drawContent()
                            val played = (positionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                            val buffered = (bufferedMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(played, 1f)
                            drawRect(
                                color = Color.White.copy(alpha = BUFFERED_ALPHA),
                                topLeft = Offset(size.width * played, 0f),
                                size = Size(size.width * (buffered - played), size.height),
                            )
                        },
                )
            },
            modifier = Modifier.weight(1f).widthIn(min = BAR_MIN_WIDTH),
        )
        TimeText(listOf(clockTime(durationMs), endsLabel).filter(String::isNotEmpty).joinToString(" · "))
    }
}
