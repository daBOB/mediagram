// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import playback.PlaybackTotals
import player.READOUT_TICK_MS
import player.audioStatLine
import player.bufferStatLine
import player.cacheStatLine
import player.droppedStatLine
import player.readsStatLine
import player.videoStatLine

/** One row of the playback statistics: a label and the line beside it. */
data class PlaybackStat(
    val label: String,
    val value: String,
)

/**
 * What the player is really doing, as the rows both surfaces' statistics
 * overlays set out — which rows there are, in what order, and when one is
 * left out, decided once so the phone and the television cannot come to
 * report different things.
 *
 * Every number is read from the player and the byte path on each
 * recomposition and none is kept anywhere: a copy of a figure the player
 * owns could only be the same figure later, or a different one wrongly.
 * Nothing is threaded through `PlayerUiState`, which carries no position
 * for that same reason.
 *
 * It reports what is being decoded, which is not always what the catalog
 * recorded; where the title's own technical line disagrees with these rows,
 * this is the one that watched it happen.
 */
@Composable
fun playbackStats(
    player: Player,
    totals: () -> PlaybackTotals,
): List<PlaybackStat> {
    // The transport bar's own tick, shared rather than started again. These
    // two positions are the only snapshot state read here, and so the only
    // thing that recomposes the caller: every other figure below is a plain
    // property read that nothing observes, and without this one nothing would
    // ever bring them up to date.
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)
    val aheadMs = (progress.bufferedPositionMs - progress.currentPositionMs).coerceAtLeast(0L)

    // The decoded formats and the renderer's counters belong to ExoPlayer,
    // not to the Player interface this is handed. It is this app's own
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
    val dropped =
        exo?.videoDecoderCounters?.let {
            it.ensureUpdated()
            it.droppedBufferCount
        } ?: 0

    return listOfNotNull(
        PlaybackStat(
            label = "video",
            // A width or height of zero is no more a size than media3's own
            // -1 for unset, and either means the format is not resolved yet.
            value =
                videoStatLine(
                    width = video?.width?.takeIf { it > 0 },
                    height = video?.height?.takeIf { it > 0 },
                    codec = video?.sampleMimeType,
                    bitrate = video?.bitrate,
                ),
        ),
        audio?.sampleMimeType?.let { codec ->
            PlaybackStat(label = "audio", value = audioStatLine(codec, audio.channelCount, audio.language.orEmpty()))
        },
        PlaybackStat(label = "buffer", value = bufferStatLine(aheadMs)),
        PlaybackStat(label = "cache", value = cacheStatLine(counted)),
        PlaybackStat(label = "reads", value = readsStatLine(counted)),
        droppedStatLine(dropped)?.let { PlaybackStat(label = "dropped", value = it) },
    )
}
