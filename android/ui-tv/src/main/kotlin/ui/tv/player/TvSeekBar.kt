package ui.tv.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.clockTime

/** Finds the seek bar in a test without depending on how it is drawn. */
internal const val TvSeekBarTag = "tv-seek-bar"

/**
 * Where the film is, as a bar the remote can stand on. Focusable but not
 * draggable — a remote has nothing to drag with — and it moves nothing by
 * itself: Left and Right on it are the player screen's to answer (see
 * [TvPlayerRemote]), fifteen seconds a press and faster the longer a key is
 * held, because the screen already holds every other key and a second
 * handler here would be a second place deciding what an arrow means.
 *
 * Focus has to read across a room, and a bar a few pixels high cannot grow
 * a border that does: focused, it thickens, takes the catalogue's accent
 * and gains a thumb at the playhead — the same accent every other focused
 * thing on this surface wears.
 *
 * [down] is where Down goes from here: the first of the card's tools, rather
 * than whichever control happens to sit nearest. [up], while there is one, is
 * where Up goes: the up-next card's Play now while it floats above the card,
 * otherwise the top bar's first control.
 */
@Composable
internal fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    down: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    up: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val track = if (focused) FocusedTrack else Track
    val fill = if (focused) Palette.Imprint else Palette.Text

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Reach)
                .focusRequester(focusRequester)
                .focusProperties {
                    this.down = down
                    if (up != null) this.up = up
                }
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }.focusable()
                .testTag(TvSeekBarTag)
                .semantics {
                    contentDescription = "Seek bar"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                }.drawBehind {
                    val thickness = track.toPx()
                    val top = (size.height - thickness) / 2
                    drawRect(color = Palette.RuleStrong, topLeft = Offset(0f, top), size = Size(size.width, thickness))
                    drawRect(color = fill, topLeft = Offset(0f, top), size = Size(size.width * fraction, thickness))
                    if (focused) {
                        drawCircle(color = fill, radius = Thumb.toPx(), center = Offset(size.width * fraction, size.height / 2))
                    }
                },
    )
}

/** The bar's own height, room for the focused thumb, so focusing it does not shift the rows around it. */
private val Reach = 28.dp
private val Track = 6.dp
private val FocusedTrack = 10.dp
private val Thumb = 12.dp

/**
 * Row one of the card, as on the phone and the web: where the film is, the
 * bar, then how long it runs and, after a dot, when it will end by the clock
 * on the wall. [ends] is blank while neither the catalogue nor media3 knows
 * the length.
 */
@Composable
internal fun TvSeekRow(
    positionMs: Long,
    durationMs: Long,
    ends: String,
    focusRequester: FocusRequester,
    down: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    up: FocusRequester? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = clockTime(positionMs), style = TvTypeScale.body, color = Palette.Text)
        TvSeekBar(
            positionMs = positionMs,
            durationMs = durationMs,
            focusRequester = focusRequester,
            down = down,
            up = up,
            onFocusChanged = onFocusChanged,
            modifier = Modifier.weight(1f),
        )
        Text(text = listOf(clockTime(durationMs), ends).filter(String::isNotEmpty).joinToString(" · "), style = TvTypeScale.body, color = Palette.Figures)
    }
}
