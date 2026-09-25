package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import playback.PlaybackTotals
import ui.player.SCRIM_ALPHA
import ui.player.playbackStats

/** Finds the statistics in a test without depending on what they read. */
internal const val TvStatsOverlayTag = "tv-stats-overlay"

/**
 * The phone's playback statistics on a television: the same rows from the
 * same [playbackStats], in a monospace table so a column of digits reads
 * down rather than across — somebody's instrument, not a printed page, on
 * this surface as on the phone. Only read, never focused: there is nothing
 * in it to press, so the remote stays on the controls that toggled it.
 */
@Composable
internal fun TvStatsOverlay(
    player: Player,
    totals: () -> PlaybackTotals,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .padding(Spacing.medium)
                .testTag(TvStatsOverlayTag),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        playbackStats(player, totals).forEach { stat ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text(
                    text = stat.label,
                    style = Mono,
                    color = Palette.Figures,
                    modifier = Modifier.width(LabelWidth),
                )
                Text(text = stat.value, style = Mono, color = Palette.Text)
            }
        }
    }
}

private val Mono = TvTypeScale.body.copy(fontFamily = FontFamily.Monospace)

/** Wide enough for `dropped`, the longest label, at the television's reading size, so every value starts at the same x. */
private val LabelWidth = 88.dp
