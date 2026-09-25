package ui.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import designsystem.Spacing
import playback.Framing
import playback.TimedCue
import player.PlayerChoices

/**
 * The phone's subtitle sizing. 18sp is the unscaled cue text size, the one
 * the viewer's size choice is a percentage of — the browser's own default
 * is comparably sized against a similar viewing distance.
 */
private val PhoneSubtitles = SubtitleMetrics(
    baseSize = 18.sp,
    sideMargin = Spacing.large,
    bottomMargin = Spacing.large,
    boxPadding = PaddingValues(horizontal = Spacing.small, vertical = Spacing.extraSmall),
)

/**
 * The picture with its subtitles drawn over it — [barTop] is the transport
 * bar's top edge in root coordinates while the bar is on screen, `null`
 * while it is hidden. [onPictureBottomChanged] reports the visible
 * picture's own bottom edge, root-coordinate too, for `PlayerScreen` to
 * clamp the up-next card to while the bar is hidden.
 *
 * [isInPip] forces [Framing.FIT] regardless of what the show remembers —
 * a device check found a remembered 4:3/16:9 crop letterboxing a second
 * time inside a picture-in-picture window already shaped to the video's
 * own aspect (see `PipActions.buildPipParams`). [choices] itself is never
 * written for this, so the remembered framing is exactly back the moment
 * the window closes, nothing to restore by hand.
 */
@Composable
internal fun VideoWithSubtitles(
    player: Player,
    cues: List<TimedCue>,
    choices: PlayerChoices,
    barTop: Float?,
    isInPip: Boolean,
    onPictureBottomChanged: (Float) -> Unit,
) {
    val framing = if (isInPip) Framing.FIT else choices.framing
    Video(player, framing = framing, onWindowBottomChanged = onPictureBottomChanged) {
        SubtitleLayer(
            player = player, cues = cues, barTop = barTop,
            sizePercent = choices.subtitleSizePercent,
            backing = choices.subtitleBacking,
            offsetMs = choices.subtitleOffsetMs,
            metrics = PhoneSubtitles,
        )
    }
}
