// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import data.ResumePoint
import designsystem.Spacing
import player.endsAtLabel
import player.speedLabel

/**
 * How often the readout catches up with the playhead. Twice a second: a clock
 * printing whole seconds needs no more, and a tick is a recomposition.
 *
 * Shared with the statistics overlay rather than copied, so the two read the
 * player on one interval instead of drifting apart on two.
 */
internal const val TICK_MS = 500L

/**
 * Enough to keep white legible over a bright frame without hiding it. The
 * overlay lays the same scrim over the picture, and one film cannot sit
 * under two different greys.
 */
internal const val SCRIM_ALPHA = 0.55f

/**
 * The transport bar.
 *
 * Everything shown here is a fact ExoPlayer already keeps, read through
 * media3's own state holders rather than carried through the ViewModel — see
 * `feature/player/build.gradle.kts` for where that line is drawn and why. The
 * holders start and stop observing with the composition, so nothing here runs
 * a timer or removes a listener.
 *
 * Glyphs rather than icons: `PlayerScreen` already draws its back arrow as
 * text, and five more characters do not earn an artifact.
 */
@Composable
fun PlayerControls(
    player: Player,
    onScrubbingChanged: (Boolean) -> Unit,
    statsShown: Boolean,
    onToggleStats: () -> Unit,
    speed: Float,
    onOpenSettings: () -> Unit,
    /** The catalogue's own runtime, in whole seconds — trusted over media3's until it has one; see [ResumePoint.trustedRuntime]. */
    catalogedDurationSecs: Int?,
    /** Whether a next title exists at all — the standing button stays even once the up-next card is cancelled. */
    hasNext: Boolean,
    nextTitleLine: String,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)

    // Null except mid-drag, when it holds where the thumb is rather than
    // where the film is. A slider snapped back to the playhead twice a
    // second could not be dragged at all — the web solves this the same way.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrubbingTo == null) { onScrubbingChanged(scrubbingTo != null) }

    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val positionMs = scrubbingTo?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)

    // The catalogue's runtime first (known before media3 has buffered
    // enough to report its own), falling back to media3's once there is
    // one — never a transcode's still-growing length here, unlike the
    // web's own case. Counted from the playhead, not the scrub thumb,
    // which only previews where a seek would land.
    val runtimeSeconds = ResumePoint.trustedRuntime(
        catalogued = catalogedDurationSecs?.toDouble(),
        observed = progress.durationMs.takeIf { it > 0 }?.let { it / 1_000.0 },
        direct = true,
    ).takeIf { it > 0 }
    val endsLabel = endsAtLabel(
        runtimeSeconds = runtimeSeconds,
        positionSeconds = progress.currentPositionMs.coerceAtLeast(0L) / 1_000.0,
        speed = speed,
        nowMs = System.currentTimeMillis(),
    )

    // The app draws edge to edge, and the route that hosts this bar gives the
    // whole window to the picture, system bars included — a film is the one
    // thing here that wants that space. This bar is the exception that
    // decision has to make: on the bottom edge it would put the clock row
    // behind a three-button navigation bar and the lower half of the slider
    // inside it, where a drag meant for the scrubber lands on the navigation
    // bar instead. Bottom and sides only, because the status bar cannot reach
    // a bar anchored down here and padding for it would open a band of dead
    // scrim above the buttons. The background is applied before the padding,
    // so the scrim still runs to the edge of the screen; only the controls
    // move in.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(Spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Both labels are read back off the player rather than written
            // here, so a button cannot come to say one thing and do another.
            GlyphButton(
                glyph = "⏪",
                description = "Skip back ${seekBack.seekBackAmountMs / 1_000} seconds",
                enabled = seekBack.isEnabled,
                onClick = seekBack::onClick,
            )
            GlyphButton(
                glyph = if (playPause.showPlay) "▶" else "⏸",
                description = if (playPause.showPlay) "Play" else "Pause",
                enabled = playPause.isEnabled,
                onClick = playPause::onClick,
            )
            GlyphButton(
                glyph = "⏩",
                description = "Skip forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
                enabled = seekForward.isEnabled,
                onClick = seekForward::onClick,
            )
            // Last, after the three that move the film: it does not move
            // it, so it sits apart from that group rather than inside it.
            GlyphButton(
                glyph = "ⓘ",
                description = if (statsShown) "Hide playback statistics" else "Show playback statistics",
                enabled = true,
                onClick = onToggleStats,
            )
            // Standing, not just in the card: cancelling the card's own offer
            // never withdraws this one.
            if (hasNext) {
                GlyphButton(
                    glyph = "⏭",
                    description = if (nextTitleLine.isNotEmpty()) "Play next: $nextTitleLine" else "Play next",
                    enabled = true,
                    onClick = onPlayNext,
                )
            }
            // As the web shows it: a number beside the gear only while it differs from the default.
            if (speed != 1f) TimeText(speedLabel(speed))
            GlyphButton(glyph = "⚙", description = "Playback settings", enabled = true, onClick = onOpenSettings)
        }
        PlayerScrubber(
            positionMs = positionMs,
            durationMs = durationMs,
            endsLabel = endsLabel,
            scrubbingTo = scrubbingTo,
            onScrubbingToChange = { scrubbingTo = it },
            onSeek = player::seekTo,
        )
    }
}
