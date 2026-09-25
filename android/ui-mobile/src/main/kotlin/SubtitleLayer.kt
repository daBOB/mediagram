package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.media3.common.Player
import designsystem.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import playback.TimedCue
import playback.activeCues
import playback.cueAppearance
import player.PlayerChoices

/** How often the playhead is re-read while cues are loaded — a tick, not a listener, the same way `PlayerControls`' own clock is: 100ms is fine enough that a cue's own start never visibly lags it. */
private const val SUBTITLE_TICK_MS = 100L

/** The unscaled cue text size; [CueAppearance.fontScale] is a percentage of this — the browser's own default is comparably sized against a similar viewing distance. */
private const val BASE_CUE_SIZE_SP = 18

/**
 * The picture with its subtitles drawn over it — [barTop] is the transport
 * bar's top edge in root coordinates while the bar is on screen, `null`
 * while it is hidden. [onPictureBottomChanged] reports the visible
 * picture's own bottom edge, root-coordinate too, for `PlayerScreen` to
 * clamp the up-next card to while the bar is hidden.
 */
@Composable
internal fun VideoWithSubtitles(
    player: Player,
    cues: List<TimedCue>,
    choices: PlayerChoices,
    barTop: Float?,
    onPictureBottomChanged: (Float) -> Unit,
) {
    Video(player, framing = choices.framing, onWindowBottomChanged = onPictureBottomChanged) {
        SubtitleLayer(
            player = player, cues = cues, barTop = barTop,
            sizePercent = choices.subtitleSizePercent,
            backing = choices.subtitleBacking,
            offsetMs = choices.subtitleOffsetMs,
        )
    }
}

/**
 * The subtitle text for whichever of [cues] is active at the playhead,
 * bottom-centred inside the visible picture (this composable is only ever
 * called inside `Video`'s own overlay box, which `Video` already sizes to
 * `Framed.window` — the whole screen for `Fit`/`Fill`, or the smaller,
 * fixed-shape window a named ratio crops into, never the raw box the
 * picture itself was cropped from, which may run bigger than either; see
 * `playback.frame`). Ticks its own position while [cues] is non-empty
 * rather than reading `PlayerControls`' clock, which only exists while the
 * transport bar is shown — a cue has to keep moving with the bar hidden too.
 *
 * Lifted by exactly as much of the picture as the bar covers, measured
 * rather than guessed: the bar's height differs by device and font scale,
 * and on a letterboxed portrait screen it may not reach the picture at all.
 */
@Composable
internal fun BoxScope.SubtitleLayer(
    player: Player,
    cues: List<TimedCue>,
    sizePercent: Int,
    backing: String,
    offsetMs: Long,
    barTop: Float?,
) {
    if (cues.isEmpty()) return

    var pictureBottom by remember { mutableFloatStateOf(0f) }
    Box(Modifier.matchParentSize().onGloballyPositioned { pictureBottom = it.boundsInRoot().bottom })
    val covered = with(LocalDensity.current) { (pictureBottom - (barTop ?: pictureBottom)).coerceAtLeast(0f).toDp() }

    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player, cues) {
        while (isActive) {
            positionMs = player.currentPosition
            delay(SUBTITLE_TICK_MS)
        }
    }

    val active = remember(cues, positionMs, offsetMs) { activeCues(cues, positionMs, offsetMs) }
    if (active.isEmpty()) return
    val text = remember(active) { active.joinToString("\n") { it.text } }
    val appearance = remember(sizePercent, backing) { cueAppearance(sizePercent, backing) }

    val shadow = if (appearance.hasShadow) {
        Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 6f)
    } else {
        null
    }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
                start = Spacing.large,
                end = Spacing.large,
                bottom = covered + Spacing.large,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = (BASE_CUE_SIZE_SP * appearance.fontScale).sp,
            style = TextStyle(shadow = shadow),
            modifier = if (appearance.backgroundAlpha > 0f) {
                Modifier
                    .background(Color.Black.copy(alpha = appearance.backgroundAlpha))
                    .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall)
            } else {
                Modifier
            },
        )
    }
}
