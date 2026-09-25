// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * The three buttons that move the film — back ten, play/pause, forward ten
 * — the phone's transport row, read through the same media3 state holders
 * the phone reads, so a label cannot come to say one thing and do another
 * and nothing about the player is carried through the ViewModel.
 *
 * Up from any of them goes to the seek bar ([up]), the one row above. What
 * sits below is left to whatever the screen puts there next.
 */
@Composable
internal fun TvTransport(
    player: Player,
    playPauseFocus: FocusRequester,
    up: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val playPause = rememberPlayPauseButtonState(player)
    val seekBack = rememberSeekBackButtonState(player)
    val seekForward = rememberSeekForwardButtonState(player)
    val toSeekBar = Modifier.focusProperties { this.up = up }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvGlyphButton(
            glyph = "⏪",
            description = "Skip back ${seekBack.seekBackAmountMs / 1_000} seconds",
            enabled = seekBack.isEnabled,
            onClick = seekBack::onClick,
            modifier = toSeekBar,
        )
        TvGlyphButton(
            glyph = if (playPause.showPlay) "▶" else "⏸",
            description = if (playPause.showPlay) "Play" else "Pause",
            enabled = playPause.isEnabled,
            onClick = playPause::onClick,
            modifier = toSeekBar.focusRequester(playPauseFocus),
        )
        TvGlyphButton(
            glyph = "⏩",
            description = "Skip forward ${seekForward.seekForwardAmountMs / 1_000} seconds",
            enabled = seekForward.isEnabled,
            onClick = seekForward::onClick,
            modifier = toSeekBar,
        )
    }
}

/**
 * A transport control drawn as a character, named for a screen reader —
 * a glyph has no accessible text of its own. Glyphs rather than icons, as
 * on the phone: this surface has no icon set, and three characters do not
 * earn one.
 *
 * The house focus treatment ([TvFocus]) rather than a stock button, so a
 * focused control grows and takes the accent border the way every other
 * focused thing on this surface does. Transparent until focused: over a
 * film, a row of filled chips would be three more things to look at.
 */
@Composable
internal fun TvGlyphButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = description },
        shape = TvFocus.surfaceShape(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Palette.Text,
                focusedContainerColor = Palette.Sunk,
                focusedContentColor = Palette.Imprint,
                pressedContainerColor = Palette.Sunk,
                pressedContentColor = Palette.Imprint,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Palette.Figures,
            ),
        scale = TvFocus.surfaceScale(),
        border = TvFocus.surfaceBorder(),
        glow = TvFocus.surfaceGlow(),
    ) {
        Text(
            text = glyph,
            style = TvTypeScale.title,
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small),
        )
    }
}
