// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import designsystem.Spacing
import ui.common.player.TransportIcons

/**
 * The card's bottom row: ↺, ⏮, back fifteen, play/pause, forward fifteen,
 * ⏭, then ⓘ (dimmed while the statistics are off, as on the phone) and ☰ — the web's transport, read through the same media3
 * state holders the phone reads, so a label cannot come to say one thing
 * and do another and nothing about the player is carried through the
 * ViewModel.
 *
 * ⏮ and ⏭ walk the run, the same switch up next takes; with no run they
 * are left out rather than drawn dead, and at either end the one with
 * nowhere to go is dimmed. ↺ is the only restart: ⏮ never means "back to
 * the start of this one". ☰ is there while the run has an episode list.
 *
 * Up from any of them reaches the tools, at CC — the first of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvTransport(
    player: Player,
    focus: TvPlayerFocus,
    extras: TvPlayerExtras,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val run = extras.upNext
    val toTools = Modifier.focusProperties { this.up = focus.cc }
    // ↺ starts the row, and ☰ — or ⓘ without a list — ends it: nothing lies beyond either.
    val atStart = Modifier.focusProperties { left = FocusRequester.Cancel }
    val atEnd = Modifier.focusProperties { right = FocusRequester.Cancel }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TvGlyphButton(glyph = "↺", description = "Restart", enabled = true, onClick = extras.onRestart, modifier = toTools.then(atStart), padding = Spacing.small)
        if (run.inRun) {
            TvIconButton(icon = TransportIcons.Previous, description = "Previous", enabled = run.hasPrevious, onClick = extras.onPrevious, modifier = toTools)
        }
        TvGlyphButton(
            glyph = "−${seekBack.seekBackAmountMs / 1_000}",
            description = "Back ${seekBack.seekBackAmountMs / 1_000} seconds",
            enabled = seekBack.isEnabled,
            onClick = seekBack::onClick,
            modifier = toTools,
            padding = Spacing.small,
        )
        TvIconButton(
            icon = if (playPause.showPlay) TransportIcons.Play else TransportIcons.Pause,
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
            modifier = toTools.focusRequester(focus.playPause),
        )
        TvGlyphButton(
            glyph = "+${seekForward.seekForwardAmountMs / 1_000}",
            description = "Forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
            enabled = seekForward.isEnabled,
            onClick = seekForward::onClick,
            modifier = toTools,
            padding = Spacing.small,
        )
        if (run.inRun) {
            TvIconButton(icon = TransportIcons.Next, description = "Next", enabled = run.hasNext, onClick = extras.onPlayNext, modifier = toTools)
        }
        TvGlyphButton(
            glyph = "ⓘ",
            description = "Stats",
            enabled = true,
            onClick = extras.onToggleStats,
            modifier = (if (extras.onOpenEpisodes == null) toTools.then(atEnd) else toTools).semantics { stateDescription = if (extras.statsShown) "On" else "Off" },
            padding = Spacing.small,
            dimmed = !extras.statsShown,
        )
        extras.onOpenEpisodes?.let { open ->
            TvGlyphButton(glyph = "☰", description = "Episodes", enabled = true, onClick = open, modifier = toTools.then(atEnd).focusRequester(focus.episodes), padding = Spacing.small)
        }
    }
}
