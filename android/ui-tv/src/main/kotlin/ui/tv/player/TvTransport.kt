// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import designsystem.Spacing
import ui.player.TransportIcons

/**
 * The three buttons that move the film — back ten, play/pause, forward ten
 * — and "Play next" after them while the run has a next title: the phone's
 * transport row, read through the same media3 state holders
 * the phone reads, so a label cannot come to say one thing and do another
 * and nothing about the player is carried through the ViewModel.
 *
 * Only what moves the film. The tools that do not — Notes, the settings
 * gear and the statistics — stand at the end of the row below
 * ([TvToolGroup]), which has width to spare: this row, with them in it, ran
 * past the edge of a stage the notes column had narrowed, and the gear
 * could not be reached while the notes were open. Drawn close for the same
 * reason.
 *
 * Up from any of them goes to the seek bar, the one row above; Down goes to
 * the row below ([down]).
 */
@Composable
internal fun TvTransport(
    player: Player,
    focus: TvPlayerFocus,
    down: FocusRequester,
    extras: TvPlayerExtras,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val toSeekBar =
        Modifier.focusProperties {
            this.up = focus.seekBar
            this.down = down
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIconButton(
            icon = TransportIcons.SkipBack,
            description = "Skip back ${seekBack.seekBackAmountMs / 1_000} seconds",
            enabled = seekBack.isEnabled,
            onClick = seekBack::onClick,
            modifier = toSeekBar,
        )
        TvIconButton(
            icon = if (playPause.showPlay) TransportIcons.Play else TransportIcons.Pause,
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
            modifier = toSeekBar.focusRequester(focus.playPause),
        )
        TvIconButton(
            icon = TransportIcons.SkipForward,
            description = "Skip forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
            enabled = seekForward.isEnabled,
            onClick = seekForward::onClick,
            modifier = toSeekBar,
        )
        // Standing, not just in the card, as on the phone: cancelling the
        // card's offer never withdraws this one. Beside the skips rather
        // than after the statistics toggle, where the phone has it, since
        // this row gathers everything that moves the film.
        if (extras.hasNext) {
            TvIconButton(
                icon = TransportIcons.Next,
                description = if (extras.nextTitleLine.isNotEmpty()) "Play next: ${extras.nextTitleLine}" else "Play next",
                enabled = true,
                onClick = extras.onPlayNext,
                modifier = toSeekBar,
            )
        }
    }
}
