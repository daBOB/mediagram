// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import designsystem.Spacing

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
 * Glyphs rather than icons: this module has no Material icons dependency, and
 * `PlayerScreen` already draws its back arrow as text. Five more characters do
 * not earn an artifact.
 */
@Composable
fun PlayerControls(
    player: Player,
    onScrubbingChanged: (Boolean) -> Unit,
    onToggleStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, TICK_MS)

    // Null except while a drag is under way, when it holds where the thumb is
    // rather than where the film is. A slider snapped back to the playhead
    // twice a second could not be dragged at all — the same problem the web
    // player solves by refusing to move its slider while it holds focus.
    var scrubbingTo by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrubbingTo == null) { onScrubbingChanged(scrubbingTo != null) }

    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val positionMs = scrubbingTo?.toLong() ?: progress.currentPositionMs.coerceAtLeast(0L)

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
            // Last, after the three that move the film, because it does not
            // move it: the transport controls stay a group of three and the
            // one that only reports sits at the end of the row rather than
            // in among them.
            GlyphButton(
                glyph = "ⓘ",
                description = "Playback statistics",
                enabled = true,
                onClick = onToggleStats,
            )
        }
        Slider(
            value = positionMs.toFloat(),
            onValueChange = { scrubbingTo = it },
            // On release, not during: every position a thumb passes over
            // would otherwise be a seek, and every seek is a read from
            // Telegram at a fresh offset.
            onValueChangeFinished = {
                scrubbingTo?.let { player.seekTo(it.toLong()) }
                scrubbingTo = null
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
            TimeText(clockTime(durationMs))
        }
    }
}
