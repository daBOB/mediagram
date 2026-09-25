// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
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
 * What the player is really doing, in a row each.
 *
 * Every number here is read from the player and the byte path on each
 * recomposition and none is kept anywhere: a copy of a figure the player owns
 * could only be the same figure later, or a different one wrongly. Nothing is
 * threaded through `PlayerUiState`, which carries no position for that same
 * reason.
 *
 * This is the one surface in the app set as a table in a monospace face
 * rather than in the catalogue's own type. It is not a printed thing — it
 * is somebody's instrument, opened to read columns of digits, and digits
 * that do not line up are the thing it is worst at.
 *
 * It reports what is being decoded, which is not always what the catalog
 * recorded; where the title's own technical line disagrees with these rows,
 * this is the one that watched it happen.
 */
@Composable
fun PlaybackStatsOverlay(
    player: Player,
    totals: () -> PlaybackTotals,
    held: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // The transport bar's own tick, shared rather than started again. These
    // two positions are the only snapshot state the overlay reads, and so the
    // only thing that recomposes it: every other figure below is a plain
    // property read that nothing observes, and without this one nothing would
    // ever bring them up to date.
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)
    val aheadMs = (progress.bufferedPositionMs - progress.currentPositionMs).coerceAtLeast(0L)

    // The decoded formats and the renderer's counters belong to ExoPlayer,
    // not to the Player interface this screen is handed. It is this app's own
    // player, so the cast holds; writing it as one that can fail means an
    // overlay that reports less rather than one that crashes if that stops
    // being true.
    val exo = player as? ExoPlayer
    val video = exo?.videoFormat
    val audio = exo?.audioFormat
    val counted = totals()

    // The renderer writes these on the playback thread. ensureUpdated's body
    // is empty and synchronized: it exists to be the barrier that makes those
    // writes visible to a reader on another thread, which is what this is.
    val dropped = exo?.videoDecoderCounters?.let {
        it.ensureUpdated()
        it.droppedBufferCount
    } ?: 0

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .padding(Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        StatRow(
            label = "video",
            // A width or height of zero is no more a size than media3's own
            // -1 for unset, and either means the format is not resolved yet.
            value = videoStatLine(
                width = video?.width?.takeIf { it > 0 },
                height = video?.height?.takeIf { it > 0 },
                codec = video?.sampleMimeType,
                bitrate = video?.bitrate,
            ),
        )
        audio?.sampleMimeType?.let { codec ->
            StatRow(
                label = "audio",
                value = audioStatLine(codec, audio.channelCount, audio.language.orEmpty()),
            )
        }
        StatRow(label = "buffer", value = bufferStatLine(aheadMs, held))
        StatRow(label = "cache", value = cacheStatLine(counted))
        StatRow(label = "reads", value = readsStatLine(counted))
        droppedStatLine(dropped)?.let { StatRow(label = "dropped", value = it) }
    }
}

/** One label and one line, the label in a column of its own so the values align. */
@Composable
private fun StatRow(label: String, value: String) {
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
