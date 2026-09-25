package ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import playback.Framing

/**
 * Touch over the picture — phone-native input for decisions the web already
 * made (CLAUDE.md § Surface Parity). A tap toggles the transport bar,
 * delayed by the double-tap timeout the same as any video app; a double tap
 * in the outer thirds seeks by whatever [Player.getSeekBackIncrement]/
 * [Player.getSeekForwardIncrement] already are — the buttons' own amount,
 * never a second number kept in step with them by hand — and in the middle
 * toggles play/pause. A pinch sets [Framing.FILL] or [Framing.FIT]
 * directly, the Android idiom standing in for the web's `z` key, which this
 * app has no keyboard for; the other two framings are the sheet's own rows.
 *
 * Attached to the whole screen rather than measured against the video box:
 * the transport bar, the settings sheet and the up-next card all consume
 * their own taps before this modifier ever sees one, so nothing here has to
 * carve the video area out by hand — see `PlayerScreen`.
 */
@Composable
internal fun PlayerGestureLayer(
    player: Player?,
    onToggleControls: () -> Unit,
    onPinchFraming: (Framing) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var flash by remember { mutableStateOf<SeekFlash?>(null) }
    Box(
        modifier = modifier
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        player?.let { flash = handleDoubleTap(it, offset.x, size.width.toFloat(), flash) }
                    },
                )
            }
            .pointerInput(Unit) { detectPinchFraming(onPinchFraming) },
        contentAlignment = Alignment.Center,
    ) {
        content()
        SeekRipple(flash = flash, modifier = Modifier.align(Alignment.Center))
    }
}

/** Which third of [width] a tap at [x] falls into. */
internal fun seekZoneFor(x: Float, width: Float): SeekZone = when {
    width <= 0f -> SeekZone.PLAY_PAUSE
    x < width / 3f -> SeekZone.BACK
    x > width * 2f / 3f -> SeekZone.FORWARD
    else -> SeekZone.PLAY_PAUSE
}

/**
 * What a double tap at [tapX] (out of [width]) does to [player], and what
 * it leaves for [SeekRipple] to show — [previous] is the flash still
 * showing, threaded through so a second double-tap on the same side
 * accumulates onto it instead of replacing it; see [accumulateFlash]. The
 * middle zone leaves it untouched: toggling play/pause is not a seek, and
 * has nothing of its own worth flashing.
 */
internal fun handleDoubleTap(player: Player, tapX: Float, width: Float, previous: SeekFlash?): SeekFlash? =
    when (seekZoneFor(tapX, width)) {
        SeekZone.BACK -> {
            player.seekBack()
            accumulateFlash(previous, SeekZone.BACK, player.seekBackIncrement / 1_000, System.currentTimeMillis())
        }
        SeekZone.FORWARD -> {
            player.seekForward()
            accumulateFlash(previous, SeekZone.FORWARD, player.seekForwardIncrement / 1_000, System.currentTimeMillis())
        }
        SeekZone.PLAY_PAUSE -> {
            // Not `if (isPlaying) pause() else play()`: `isPlaying` is false
            // during `STATE_BUFFERING` even with `playWhenReady` true, which
            // this app's slow byte path hits often, and `play()` then does
            // nothing — the same buffering-aware toggle the transport
            // button already uses (`rememberPlayPauseButtonState`), so the
            // gesture and the button never disagree about what a tap does.
            Util.handlePlayPauseButtonAction(player)
            previous
        }
    }

/** How far the fingers must move apart, or together, before a pinch counts as one — checked against the gesture's own total rather than one frame's delta, since a slow pinch moves a fraction of this on any single frame. */
private const val PINCH_OUT_ZOOM = 1.2f
private const val PINCH_IN_ZOOM = 1f / PINCH_OUT_ZOOM

/**
 * Fires [onFraming] once per pinch, the moment it crosses [PINCH_OUT_ZOOM]
 * or [PINCH_IN_ZOOM] — never per frame, which `detectTransformGestures`
 * alone would, and never twice for one gesture a viewer's fingers kept
 * moving after the choice was already made. A single moving finger never
 * trips this: [calculateZoom] answers exactly `1f` unless two pointers are
 * down to measure a distance between.
 *
 * Two pointers down (or a pinch this gesture already fired) are consumed
 * outright, on every event for the rest of the gesture — never a tap or a
 * double-tap too: this modifier sits beside `detectTapGestures` on the same
 * node, sees the `Main` pass first (it is the later of the two
 * `pointerInput`s attached, and `Main` runs child-first), and a change it
 * consumes here is one the tap detector then finds already consumed and
 * cancels for, rather than firing once the last finger lifts.
 *
 * A change already consumed elsewhere — the scrubber, a control — is never
 * treated as half of this gesture's own pinch, whatever the raw distance
 * between two pointers says: [calculateZoom] does not know or care which
 * finger is on what, so this checks first.
 */
private suspend fun PointerInputScope.detectPinchFraming(onFraming: (Framing) -> Unit) {
    awaitEachGesture {
        var totalZoom = 1f
        var fired = false
        do {
            val event = awaitPointerEvent()
            val twoFingers = event.changes.size >= 2
            if (twoFingers && event.changes.none { it.isConsumed }) {
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f) {
                    totalZoom *= zoomChange
                    if (!fired && totalZoom >= PINCH_OUT_ZOOM) {
                        onFraming(Framing.FILL)
                        fired = true
                    } else if (!fired && totalZoom <= PINCH_IN_ZOOM) {
                        onFraming(Framing.FIT)
                        fired = true
                    }
                }
            }
            if (twoFingers || fired) {
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
