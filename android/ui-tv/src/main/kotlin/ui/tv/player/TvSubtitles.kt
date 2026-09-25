package ui.tv.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
 * Where on the picture a cue has room, in root coordinates: clear above
 * the bottom controls and the up-next card while they are up ([barTop]),
 * never above what lies along the top of the stage ([ceiling]: the title
 * and statistics), and left of the settings panel while it is open
 * ([besideLeft]). Each is `null` while what it measures is not on screen.
 */
internal class TvCueRoom(
    val barTop: Float? = null,
    val ceiling: Float? = null,
    val besideLeft: Float? = null,
)

/**
 * The picture with its subtitles over it, drawn from the viewer's shared
 * settings — size, backing and sync offset — exactly as the phone draws
 * them, inside [room]: lifted clear of the controls as on the phone, kept
 * under the top band so a long cue never prints over the title, and
 * centred in what the settings panel leaves of the picture, since a panel
 * opened to judge a subtitle size would otherwise cover the subtitle.
 */
@Composable
internal fun TvVideoWithSubtitles(
    player: Player,
    cues: List<TimedCue>,
    choices: PlayerChoices,
    room: TvCueRoom,
) {
    Video(player, framing = choices.framing) {
        var pictureRight by remember { mutableFloatStateOf(0f) }
        Box(Modifier.matchParentSize().onGloballyPositioned { pictureRight = it.boundsInRoot().right })
        val beside = with(LocalDensity.current) { room.besideLeft?.let { (pictureRight - it).coerceAtLeast(0f).toDp() } ?: 0.dp }
        Box(Modifier.matchParentSize().padding(end = beside)) {
            SubtitleLayer(
                player = player,
                cues = cues,
                sizePercent = choices.subtitleSizePercent,
                backing = choices.subtitleBacking,
                offsetMs = choices.subtitleOffsetMs,
                barTop = room.barTop,
                metrics = TvSubtitles,
                ceiling = room.ceiling,
            )
        }
    }
}
