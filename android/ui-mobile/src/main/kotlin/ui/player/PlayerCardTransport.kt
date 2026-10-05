// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import designsystem.Spacing
import player.UpNextUiState

/**
 * The card's bottom row: ↺ ⏮ −15 ▶/❚❚ +15 ⏭, then ⓘ and ☰.
 *
 * ↺ goes back to 0:00 of this title; ⏮ is never that — it opens the title
 * before this one in the run, and is disabled on the first. Both ⏮ and ⏭
 * are left out for a title with no run (a film) rather than shown disabled.
 *
 * The skips are media3's own: their labels and their amount are read back
 * off the player, and `Player.seekBack`/`seekForward` keep a skip inside the
 * title — never before 0:00, never past the end, where the title then ends
 * the ordinary way and up next follows.
 */
@Composable
internal fun CardTransportRow(
    player: Player,
    upNext: UpNextUiState,
    statsShown: Boolean,
    hasEpisodes: Boolean,
    actions: PlayerCardActions,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val back = seekBack.seekBackAmountMs / 1_000
    val forward = seekForward.seekForwardAmountMs / 1_000

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(glyph = "↺", description = "Restart", enabled = true, onClick = actions.onRestart)
        if (upNext.inRun) {
            TransportButton(icon = TransportIcons.Previous, description = "Previous", enabled = upNext.hasPrevious, onClick = actions.onPrevious)
        }
        GlyphButton(glyph = "−$back", description = "Back $back seconds", enabled = seekBack.isEnabled, onClick = seekBack::onClick)
        TransportButton(
            icon = if (playPause.showPlay) TransportIcons.Play else TransportIcons.Pause,
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
        )
        GlyphButton(glyph = "+$forward", description = "Forward $forward seconds", enabled = seekForward.isEnabled, onClick = seekForward::onClick)
        if (upNext.inRun) {
            TransportButton(icon = TransportIcons.Next, description = "Next", enabled = upNext.hasNext, onClick = actions.onNext)
        }
        // Apart from what moves the film: ⓘ only reports, ☰ only lists.
        GlyphButton(glyph = "ⓘ", description = "Stats", enabled = true, onClick = actions.onToggleStats, dimmed = !statsShown)
        if (hasEpisodes) GlyphButton(glyph = "☰", description = "Episodes", enabled = true, onClick = actions.onEpisodes)
    }
}
