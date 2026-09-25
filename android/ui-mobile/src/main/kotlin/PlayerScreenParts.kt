// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.rememberPresentationState
import designsystem.Spacing
import model.MediaSet
import playback.Framing
import playback.frame
import playback.PlaybackTotals
import player.titleLine

/**
 * The pieces `PlayerScreen` draws, apart from the screen that arranges
 * them. Each is handed what it needs and decides nothing about when it
 * appears; the decisions stay with the screen and with
 * [ControlsVisibility]'s own functions, which is where they can be read
 * and proved.
 */

/**
 * The picture, shaped by [framing] rather than always to itself.
 *
 * `PlayerSurface` draws into whatever bounds it is given and applies no
 * ratio of its own — the old `PlayerView` had a frame layout that did — so
 * a box has to be computed and handed to it here, or filling the window
 * would stretch a 2.4:1 film onto a 3:2 display and make everyone in it
 * tall and thin.
 *
 * The video's own shape comes from media3's own presentation state rather
 * than from a listener written here: it already folds in the pixel shape
 * that makes anamorphic video 2.4:1 rather than 1.78:1, and it already
 * knows when the surface is showing a frame that no longer belongs to what
 * is playing. `BoxWithConstraints` rather than `onGloballyPositioned` for
 * this composable's own size: the latter only reports it a frame after
 * layout, so the very first frame (and the first frame after every
 * rotation, which recreates this composable's `remember`ed state along
 * with the activity) would draw full-bleed before it ever arrived —
 * `maxWidth`/`maxHeight` are already known in the same pass a `Box` is
 * measured in, so [frame] runs before anything is ever drawn stretched.
 *
 * [frame] turns the video's own shape, [framing] and this composable's own
 * measured size into a box to draw the picture into and a window to clip
 * it to — [Framing.FIT]'s window is the whole of this composable and its
 * box letterboxes within it exactly as this always did, before framing
 * existed. The other three may compute a box bigger than its window, which
 * `requiredSize` (rather than `size`, which would only shrink back to fit)
 * lets actually draw that large before the window's own `clipToBounds`
 * performs the crop.
 *
 * [overlay] draws inside a third box sized to the same window — the part
 * of the picture a viewer can actually see, which for [Framing.FIT] and
 * [Framing.FILL] is this composable's own rectangle and for the two named
 * ratios is the smaller, fixed-shape window they crop into, never the
 * (possibly bigger) box the picture was cropped from. This is what keeps
 * `SubtitleLayer` from landing outside it under a framing that crops.
 *
 * [onWindowBottomChanged] reports that same window's own bottom edge, in
 * root coordinates, the moment it is known — `PlayerScreen` clamps the
 * up-next card to it while the transport bar is hidden, the way
 * `SubtitleLayer` already clamps its own text without needing to ask.
 */
@Composable
internal fun Video(
    player: Player,
    framing: Framing = Framing.FIT,
    onWindowBottomChanged: (Float) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val presentation = rememberPresentationState(player)
    val nativeSize = presentation.videoSizeDp
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val framed = if (nativeSize != null && nativeSize.width > 0f && nativeSize.height > 0f && containerWidthPx > 0f && containerHeightPx > 0f) {
            frame(framing, nativeSize.width / nativeSize.height, containerWidthPx, containerHeightPx)
        } else {
            null
        }

        val windowModifier = if (framed != null) {
            with(density) { Modifier.size(framed.window.width.toDp(), framed.window.height.toDp()) }
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = windowModifier.clipToBounds(), contentAlignment = Alignment.Center) {
            val videoModifier = if (framed != null) {
                with(density) { Modifier.requiredSize(framed.box.width.toDp(), framed.box.height.toDp()) }
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = videoModifier) {
                PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                // Between one set and the next the surface still holds the
                // last frame of the old one. Covering it is what media3 asks
                // callers to do, and the alternative is a still from the
                // previous film over the new one's audio.
                if (presentation.coverSurface) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        val overlayModifier = if (framed != null) {
            with(density) { Modifier.size(framed.window.width.toDp(), framed.window.height.toDp()) }
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            modifier = overlayModifier.onGloballyPositioned { onWindowBottomChanged(it.boundsInRoot().bottom) },
            contentAlignment = Alignment.Center,
        ) { overlay() }
    }
}

@Composable
internal fun KeepScreenOnWhile(isPlaying: Boolean) {
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
internal fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

/**
 * The back arrow, the picture-in-picture button beside it, the title and
 * the statistics — split out of `PlayerScreen` to keep that file under the
 * project's line guideline. Absent entirely in picture-in-picture: there
 * is no touch surface of this app's own inside that window, and no room
 * for the statistics either.
 *
 * Placed by its caller with `Modifier.align(Alignment.TopStart)` rather
 * than centred here: the picture is what a `Box` centres its children
 * against, and the top bar has to be pinned to its own corner instead.
 */
@Composable
internal fun PlayerTopChrome(
    openSet: MediaSet?,
    barShown: Boolean,
    statsShown: Boolean,
    isInPip: Boolean,
    player: Player?,
    totals: () -> PlaybackTotals,
    onBack: () -> Unit,
    onEnterPip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    onNotes: (() -> Unit)? = null,
) {
    if (isInPip) return
    Column(modifier = modifier) {
        PlayerTopBar(title = titleLine(openSet), showTitle = barShown, onBack = onBack, onEnterPip = onEnterPip, onNotes = onNotes)
        // Gated on the bar being shown as well as on the toggle, so the
        // statistics have no visibility rule of their own: a viewer who
        // leaves the numbers on gets the picture back when the bar takes
        // itself away, and keeps them while the film is paused.
        if (statsShown && barShown) {
            player?.let { current ->
                PlaybackStatsOverlay(player = current, totals = totals, held = held, modifier = Modifier.padding(start = Spacing.medium))
            }
        }
    }
}
