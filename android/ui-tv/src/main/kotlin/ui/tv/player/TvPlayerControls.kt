// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import playback.PlaybackTotals
import player.PlayerMarksState
import player.READOUT_TICK_MS
import player.clockTime
import player.endsLine
import ui.player.SCRIM_ALPHA

/** The focus stops the player screen moves the remote between: one for each row it lands on. */
internal class TvPlayerFocus {
    val playPause = FocusRequester()
    val seekBar = FocusRequester()
    val settings = FocusRequester()
    val marks = FocusRequester()
    val upNext = FocusRequester()
    val notes = FocusRequester()
    val retry = FocusRequester()
    val notesRegion = FocusRequester()
}

/** What the controls show beyond the transport, and what pressing it does: the marks rail, the statistics and the settings. */
internal class TvPlayerExtras(
    val marks: PlayerMarksState?,
    val markActions: TvMarksActions,
    val statsShown: Boolean,
    val onToggleStats: () -> Unit,
    val totals: () -> PlaybackTotals,
    /** Whether this device holds the title in full, which the statistics' buffer row reports as "cached". */
    val held: Boolean = false,
    /** The chosen playback speed, read out beside the settings gear while it is not the default. */
    val speed: Float = 1f,
    val onOpenSettings: () -> Unit = {},
    /** Whether anything follows in the run — the standing "Play next" stays even once the up-next card is cancelled, as on the phone. */
    val hasNext: Boolean = false,
    val nextTitleLine: String = "",
    val onPlayNext: () -> Unit = {},
    /** Opens and closes the notes column; null while the title has none, which leaves the Notes button out. */
    val onToggleNotes: (() -> Unit)? = null,
)

/**
 * The controls over the picture: what is playing along the top, with the
 * playback statistics under it when they are on, and along the bottom the
 * clock, the seek bar, the transport and the marks rail — top to bottom in
 * the order the remote moves through them, so Down always goes further
 * from the film's own facts and further into what can be done to it.
 *
 * [upNext] is drawn above the clock at the right-hand end, the up-next
 * card when there is one: inside the same block, so the subtitles lift
 * clear of it as of the rest, and directly above the seek bar, so Up from
 * there reaches it.
 *
 * [onBarTopChanged] reports where the bottom block begins, in root
 * coordinates, so the subtitles can lift clear of it rather than be read
 * through the scrim.
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
    extras: TvPlayerExtras,
    onSeekBarFocused: (Boolean) -> Unit,
    onBarTopChanged: (Float) -> Unit = {},
    upNext: @Composable ColumnScope.() -> Unit = {},
) {
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)
    val positionMs = progress.currentPositionMs.coerceAtLeast(0L)
    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val scrim = Color.Black.copy(alpha = SCRIM_ALPHA)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            set?.let {
                TvPlayerTopBar(
                    set = it,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(scrim)
                            .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
                )
            }
            // Under what is playing, on the side the phone keeps them, and
            // inside the overscan margin like every other reading here.
            if (extras.statsShown) {
                TvStatsOverlay(
                    player = player,
                    totals = extras.totals,
                    held = extras.held,
                    modifier = Modifier.padding(start = Overscan.horizontal, top = Spacing.medium),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { onBarTopChanged(it.boundsInRoot().top) }
                    .background(scrim)
                    .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            upNext()
            TvPlayerClock(
                positionMs = positionMs,
                durationMs = durationMs,
                ends = endsLine(set?.durationSecs, positionMs, durationMs, player.playbackParameters.speed, System.currentTimeMillis()),
            )
            TvSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                focusRequester = focus.seekBar,
                down = focus.playPause,
                onFocusChanged = onSeekBarFocused,
            )
            TvTransport(
                player = player,
                focus = focus,
                // Nowhere further down while nothing is open to mark.
                down = if (extras.marks != null) focus.marks else FocusRequester.Default,
                extras = extras,
            )
            TvMarksRail(marks = extras.marks, actions = extras.markActions, first = focus.marks, up = focus.playPause)
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
