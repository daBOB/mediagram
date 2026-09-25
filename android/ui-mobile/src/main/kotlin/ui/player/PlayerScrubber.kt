package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import designsystem.Spacing

/**
 * The scrub bar and the clock row under it — split out of [PlayerControls]
 * to keep that file under the project's line guideline.
 */
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
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TimeText(clockTime(positionMs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            TimeText(clockTime(durationMs))
            if (endsLabel.isNotEmpty()) TimeText(endsLabel)
        }
    }
}
