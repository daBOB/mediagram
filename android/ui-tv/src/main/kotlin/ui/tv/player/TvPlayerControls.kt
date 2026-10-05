// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import playback.PlaybackTotals
import player.PlayerChoices
import player.PlayerMarksState
import player.READOUT_TICK_MS
import player.UpNextPhase
import player.UpNextUiState
import player.clockTime
import player.endsLine
import ui.player.SCRIM_ALPHA
import ui.player.playerCard

/** The focus stops the player screen moves the remote between — each attached for as long as its control is drawn, never added or dropped by a condition. */
internal class TvPlayerFocus {
    val playPause = FocusRequester()
    val seekBar = FocusRequester()
    val cc = FocusRequester()
    val subtitleOptions = FocusRequester()
    val speed = FocusRequester()
    val audio = FocusRequester()
    val framing = FocusRequester()
    val episodes = FocusRequester()
    val marks = FocusRequester()
    val upNext = FocusRequester()
    val notes = FocusRequester()
    val retry = FocusRequester()
    val notesRegion = FocusRequester()

    /** The control a menu or the episode list was opened from: where the remote goes back to when it closes. */
    var opener: FocusRequester = playPause

    /** The tool [menu] is opened from. */
    fun openerOf(menu: TvCardMenu): FocusRequester =
        when (menu) {
            TvCardMenu.Subtitles, TvCardMenu.SubtitleStyle -> subtitleOptions
            TvCardMenu.Speed -> speed
            TvCardMenu.Audio -> audio
            TvCardMenu.Framing -> framing
        }
}

/** What the controls show beyond the player's own state, and what pressing them does. */
internal class TvPlayerExtras(
    val marks: PlayerMarksState?,
    val markActions: TvMarksActions,
    val statsShown: Boolean,
    val onToggleStats: () -> Unit,
    val totals: () -> PlaybackTotals,
    /** Whether this device holds the title in full, which the statistics' buffer row reports as "cached". */
    val held: Boolean = false,
    /** What the tools read and change: subtitles, speed, audio and framing. */
    val choices: PlayerChoices = PlayerChoices.Default,
    val onToggleSubtitles: () -> Unit = {},
    val onOpenMenu: (TvCardMenu) -> Unit = {},
    /** The run as ⏮ and ⏭ walk it, and whether the up-next card floats above the card. */
    val upNext: UpNextUiState = UpNextUiState(),
    val onRestart: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    /** Opens the episode list; null with no list to open, which leaves ☰ out. */
    val onOpenEpisodes: (() -> Unit)? = null,
    /** Opens and closes the notes column; null while the title has none, which leaves the Notes button out. */
    val onToggleNotes: (() -> Unit)? = null,
    /** Whether the episode list is open down the right, which the card then stands clear of. */
    val sidebarOpen: Boolean = false,
)

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
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                        .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
            )
            // Under what is playing, on the side the phone keeps them, and
            // inside the overscan margin like every other reading here.
            if (extras.statsShown) {
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
            TvPlayerClock(
                positionMs = positionMs,
                durationMs = durationMs,
                ends = endsLine(set?.durationSecs, positionMs, durationMs, player.playbackParameters.speed, System.currentTimeMillis()),
            )
            TvSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
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

/** Where the film is, when it will end by the clock on the wall, and how long it runs — the phone's clock row, with the web's end time between. */
@Composable
private fun TvPlayerClock(
    positionMs: Long,
    durationMs: Long,
    ends: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = clockTime(positionMs), style = TvTypeScale.body, color = Palette.Text)
        Text(text = ends, style = TvTypeScale.body, color = Palette.Figures)
        Text(text = clockTime(durationMs), style = TvTypeScale.body, color = Palette.Text)
    }
}
