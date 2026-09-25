package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import designsystem.Spacing
import playback.PlaybackTotals

/**
 * Wide enough for `dropped`, the longest label, so every value starts at the
 * same x and a column of numbers can be read down rather than across.
 */
private val LABEL_WIDTH = 68.dp

/** Loud enough to find, quiet enough that the numbers are what is read. */
private const val LABEL_ALPHA = 0.7f

/**
 * What the player is really doing, in a row each — the rows
 * [playbackStats] reads, set out here.
 *
 * This is the one surface in the app set as a table in a monospace face
 * rather than in the catalogue's own type. It is not a printed thing — it
 * is somebody's instrument, opened to read columns of digits, and digits
 * that do not line up are the thing it is worst at.
 */
@Composable
fun PlaybackStatsOverlay(
    player: Player,
    totals: () -> PlaybackTotals,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        playbackStats(player, totals).forEach { StatRow(label = it.label, value = it.value) }
    }
}

/** One label and one line, the label in a column of its own so the values align. */
@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = LABEL_ALPHA),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
