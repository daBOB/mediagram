package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import playback.TimedCue
import playback.activeCues
import playback.cueAppearance

/** How often the playhead is re-read while cues are loaded — a tick, not a listener, the same way the controls' own clock is: 100ms is fine enough that a cue's own start never visibly lags it. */
private const val SUBTITLE_TICK_MS = 100L

/**
 * What differs between the surfaces that draw subtitles: how big the
 * unscaled text is ([baseSize] — the viewer's size choice is a percentage
 * of it), how far it keeps from the picture's sides and bottom, and the
 * padding inside a boxed backing. Everything else — which cue is active,
 * the offset, the backing, the lift above the controls — is one rule.
 */
class SubtitleMetrics(
    val baseSize: TextUnit,
    val sideMargin: Dp,
    val bottomMargin: Dp,
    val boxPadding: PaddingValues,
)

/**
 * The subtitle text for whichever of [cues] is active at the playhead,
 * bottom-centred inside the visible picture (this composable is only ever
 * called inside [Video]'s own overlay box, which [Video] already sizes to
 * `Framed.window` — the whole screen for `Fit`/`Fill`, or the smaller,
 * fixed-shape window a named ratio crops into, never the raw box the
 * picture itself was cropped from, which may run bigger than either; see
 * `playback.frame`). Ticks its own position while [cues] is non-empty
 * rather than reading the controls' clock, which only exists while the
 * controls are shown — a cue has to keep moving with them hidden too.
 *
 * [barTop] is the controls' top edge in root coordinates while they are on
 * screen, `null` while they are hidden. The text is lifted by exactly as
 * much of the picture as they cover, measured rather than guessed: their
 * height differs by device and font scale, and on a letterboxed portrait
 * screen they may not reach the picture at all.
 *
 * [ceiling], in the same root coordinates, is a line the text never rises
 * above however far [barTop] would lift it — whatever sits along the top
 * of the picture while the controls are up. Where lifting clear of both is
 * impossible, the lift gives way: a cue drawn over the edge of the
 * controls' scrim is still read, one printed over the title is not. `null`
 * lifts by the whole of [barTop], as before there was a ceiling.
 *
 * [sizePercent], [backing] and [offsetMs] are the viewer's shared subtitle
 * settings, raw as `player.PlayerChoices` keeps them; the appearance is
 * computed from them here, at the one place that draws it.
 */
@Composable
fun BoxScope.SubtitleLayer(
    player: Player,
    cues: List<TimedCue>,
    sizePercent: Int,
    backing: String,
    offsetMs: Long,
    barTop: Float?,
    metrics: SubtitleMetrics,
    ceiling: Float? = null,
) {
    if (cues.isEmpty()) return

    var pictureBottom by remember { mutableFloatStateOf(0f) }
    Box(Modifier.matchParentSize().onGloballyPositioned { pictureBottom = it.boundsInRoot().bottom })
    val density = LocalDensity.current
    val coveredPx = (pictureBottom - (barTop ?: pictureBottom)).coerceAtLeast(0f)
    val covered = with(density) { coveredPx.toDp() }
    val bottomMarginPx = with(density) { metrics.bottomMargin.toPx() }

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
                start = metrics.sideMargin,
                end = metrics.sideMargin,
                bottom = covered + metrics.bottomMargin,
            )
            // Measured against the text itself, which only this layout
            // knows the height of: the full lift, then taken back down by
            // however far the text's top would cross the ceiling.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val drop = ceiling?.let { coveredPx - liftUnder(it, coveredPx, pictureBottom - bottomMarginPx, placeable.height) } ?: 0f
                layout(placeable.width, placeable.height) { placeable.place(0, drop.roundToInt()) }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = metrics.baseSize * appearance.fontScale,
                shadow = shadow,
            ),
            modifier = if (appearance.backgroundAlpha > 0f) {
                Modifier
                    .background(Color.Black.copy(alpha = appearance.backgroundAlpha))
                    .padding(metrics.boxPadding)
            } else {
                Modifier
            },
        )
    }
}

/**
 * How far a cue [height] tall may be lifted, at most [covered], so that its
 * top stays at or below [ceiling] — when unlifted its bottom is at [restingBottom].
 * Never below nothing: a cue too tall to clear the ceiling even at rest
 * stays at rest.
 */
internal fun liftUnder(
    ceiling: Float,
    covered: Float,
    restingBottom: Float,
    height: Int,
): Float = (restingBottom - height - ceiling).coerceIn(0f, covered.coerceAtLeast(0f))
