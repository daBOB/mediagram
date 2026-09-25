package ui.tv.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import designsystem.Overscan
import playback.TimedCue
import player.PlayerChoices
import ui.player.SubtitleLayer
import ui.player.SubtitleMetrics
import ui.player.Video

/**
 * How much bigger the television draws a cue than the phone does, before
 * the viewer's own size choice — which is the phone's same percentage on
 * top of this, so "150%" means the same thing on both.
 *
 * The phone's 18sp was chosen against a picture held at arm's length, whose
 * landscape height is about 360dp. A film on a television is watched from
 * across the room, but the picture fills about the same share of the eye —
 * that is what a screen size is chosen for — so a cue should keep the same
 * share of the picture's height rather than the same sp. Every television
 * is laid out on a 540dp-high viewport, and 540 / 360 is 1.5: 27sp here.
 */
private const val TV_CUE_SCALE = 1.5f

/**
 * The television's subtitle sizing: the phone's metrics scaled by
 * [TV_CUE_SCALE], kept inside the overscan margin a set may crop — a line
 * of dialogue cut off at the edge of the screen is lost outright.
 */
internal val TvSubtitles =
    SubtitleMetrics(
        baseSize = 18.sp * TV_CUE_SCALE,
        sideMargin = Overscan.horizontal,
        bottomMargin = Overscan.vertical,
        boxPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    )

/**
 * The picture with its subtitles over it, drawn from the viewer's shared
 * settings — size, backing and sync offset — exactly as the phone draws
 * them. [barTop] is the bottom controls' top edge in root coordinates
 * while they are shown, `null` while they are away: shown, the text lifts
 * clear above them, as on the phone; away, nothing covers it.
 */
@Composable
internal fun TvVideoWithSubtitles(
    player: Player,
    cues: List<TimedCue>,
    choices: PlayerChoices,
    barTop: Float?,
) {
    Video(player, framing = choices.framing) {
        SubtitleLayer(
            player = player,
            cues = cues,
            sizePercent = choices.subtitleSizePercent,
            backing = choices.subtitleBacking,
            offsetMs = choices.subtitleOffsetMs,
            barTop = barTop,
            metrics = TvSubtitles,
        )
    }
}
