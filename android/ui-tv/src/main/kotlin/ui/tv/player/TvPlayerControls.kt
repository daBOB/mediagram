// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import model.endsAt
import player.READOUT_TICK_MS
import player.clockTime
import ui.player.SCRIM_ALPHA
import java.time.Instant

/** The two focus stops the player screen moves the remote between. */
internal class TvPlayerFocus {
    val playPause = FocusRequester()
    val seekBar = FocusRequester()
}

/**
 * The controls over the picture: what is playing along the top, and along
 * the bottom the clock, the seek bar and the transport — top to bottom in
 * the order the remote moves through them, so Down always goes further
 * from the film's own facts and further into what can be done to it.
 *
 * Inside the overscan margin, unlike the picture: a television crops its
 * edges by an amount that varies by set, and a clock or a button cut off
 * at the edge is worse than a scrim that stops short of it.
 */
@Composable
internal fun TvPlayerControls(
    player: Player,
    set: MediaSet?,
    focus: TvPlayerFocus,
    onSeekBarFocused: (Boolean) -> Unit,
) {
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)
    val positionMs = progress.currentPositionMs.coerceAtLeast(0L)
    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)

    Box(modifier = Modifier.fillMaxSize()) {
        set?.let {
            TvPlayerTopBar(
                set = it,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(scrim)
                        .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(scrim)
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            TvPlayerClock(
                positionMs = positionMs,
                durationMs = durationMs,
                ends = endsLine(set, positionMs, durationMs, player.playbackParameters.speed),
            )
            TvSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                focusRequester = focus.seekBar,
                down = focus.playPause,
                onFocusChanged = onSeekBarFocused,
            )
            TvTransport(player = player, playPauseFocus = focus.playPause, up = focus.seekBar)
        }
    }
}

/** Where the film is, when it will end by the clock on the wall, and how long it runs — the phone's clock row, with the web's end time between. */
@Composable
private fun TvPlayerClock(
    positionMs: Long,
    durationMs: Long,
    ends: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = clockTime(positionMs), style = TvTypeScale.body, color = Palette.Text)
        Text(text = ends, style = TvTypeScale.body, color = Palette.Figures)
        Text(text = clockTime(durationMs), style = TvTypeScale.body, color = Palette.Text)
    }
}

/**
 * `ends 21:40`, or nothing. The web's rule: the catalogue's runtime first,
 * the player's own length only when the catalogue has none, divided by the
 * playback speed so a viewer at 1.5× is told the truth — and blank when
 * nothing knows the length, because an end time projected from an unknown
 * one is a guess dressed as a fact.
 */
internal fun endsLine(
    set: MediaSet?,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    now: Instant = Instant.now(),
): String {
    val runtimeSeconds = set?.durationSecs?.takeIf { it > 0 }?.toDouble() ?: (durationMs / 1_000.0)
    if (runtimeSeconds <= 0) return ""
    val rate = if (speed > 0) speed else 1f
    val at = endsAt(maxOf(0.0, runtimeSeconds - positionMs / 1_000.0) / rate, now)
    return if (at.isEmpty()) "" else "ends $at"
}
