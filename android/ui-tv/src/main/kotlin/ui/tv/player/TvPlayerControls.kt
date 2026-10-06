// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import designsystem.Overscan
import designsystem.Spacing
import model.MediaSet
import player.READOUT_TICK_MS
import player.UpNextPhase
import player.endsLine
import ui.player.SCRIM_ALPHA
import ui.player.playerCard

/** Finds the controls' two bands in a test: what is playing along the top, and the card along the bottom. */
internal const val TvTopBandTag = "tv-player-top-band"
internal const val TvBottomBandTag = "tv-player-bottom-band"

/** The card's width on a television: the web's card, narrowed to what reads across a room without turning the head. */
internal val TvCardWidth = 760.dp

/** How far the card stands off the bottom — and off the episode list beside it: clear of the overscan margin, with room to breathe. */
private val TvCardInset = 32.dp

/**
 * The controls over the picture. Along the top, what is playing and the
 * marks and Notes beside it, with the playback statistics under them when
 * they are on. At the bottom, one card in three rows: where the film is and
 * when it ends, the tools, and the transport — the web's card, filled
 * rather than frosted (see `PlayerCardSurface.kt` for why Android draws no
 * blur).
 *
 * The remote lands on play/pause. Up goes to the tools, then the seek bar,
 * then the top — or to the up-next card while it floats above the card,
 * since that is what is waiting on an answer. Every hop between the rows is
 * named rather than left to geometry: the rows are different lengths, and a
 * nearest-neighbour search from the end of one lands on whatever happens to
 * sit above it.
 *
 * With the episode list open the card is laid out in the room to its left,
 * a [TvCardInset] clear of it and centred there; it narrows to fit, its
 * rows wrap, and no control gets smaller.
 *
 * Each band reports where it is to [bands] — the card its top and its
 * extent, the top band its bottom — so what floats between them keeps clear
 * of both, and a menu opens just above the tool that opened it.
 */
@Composable
internal fun TvPlayerControls(
    player: Player,
    set: MediaSet?,
    focus: TvPlayerFocus,
    extras: TvPlayerExtras,
    onSeekBarFocused: (Boolean) -> Unit,
    bands: TvStageBands,
) {
    val progress = rememberProgressStateWithTickInterval(player, READOUT_TICK_MS)
    val positionMs = progress.currentPositionMs.coerceAtLeast(0L)
    val durationMs = progress.durationMs.coerceAtLeast(0L)
    val above =
        when {
            extras.upNext.phase != UpNextPhase.HIDDEN -> focus.upNext
            extras.marks != null -> focus.marks
            extras.onToggleNotes != null -> focus.notes
            else -> null
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    // Stops where the episode list starts, as the card does, so neither reads through the other's header.
                    .padding(end = if (extras.sidebarOpen) TvSidebarWidth else 0.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { bands.topBottom = it.boundsInRoot().bottom }
                    .testTag(TvTopBandTag),
        ) {
            TvPlayerTopBar(
                set = set,
                marks = extras.marks,
                markActions = extras.markActions,
                onToggleNotes = extras.onToggleNotes,
                focus = focus,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { bands.titleBottom = it.boundsInRoot().bottom }
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                        .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            )
            // Under what is playing, on the side the phone keeps them, and
            // inside the overscan margin like every other reading here. Not
            // while the episode list is open: the card, narrowed beside it,
            // wraps a row and leaves a 540 dp screen no height for both; they
            // come back when the list closes, and Back still closes the list
            // first, then them.
            if (extras.statsShown && !extras.sidebarOpen) {
                TvStatsOverlay(
                    player = player,
                    totals = extras.totals,
                    held = extras.held,
                    modifier = Modifier.padding(start = Overscan.horizontal, top = Spacing.medium),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = if (extras.sidebarOpen) TvCardInset else Overscan.horizontal,
                        end = if (extras.sidebarOpen) TvSidebarWidth + TvCardInset else Overscan.horizontal,
                        bottom = TvCardInset,
                    )
                    .widthIn(max = TvCardWidth)
                    .fillMaxWidth()
                    .onGloballyPositioned { at ->
                        bands.barTop = at.boundsInRoot().top
                        bands.card = at.boundsInRoot().roundToIntRect()
                    }.testTag(TvBottomBandTag)
                    .playerCard()
                    .padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            TvSeekRow(
                positionMs = positionMs,
                durationMs = durationMs,
                ends = endsLine(set?.durationSecs, positionMs, durationMs, player.playbackParameters.speed, System.currentTimeMillis()),
                focusRequester = focus.seekBar,
                down = focus.cc,
                up = above,
                onFocusChanged = onSeekBarFocused,
            )
            TvToolGroup(focus = focus, extras = extras, bands = bands)
            TvTransport(player = player, focus = focus, extras = extras)
        }
    }
}
