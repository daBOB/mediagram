package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Palette
import designsystem.Spacing
import playback.FilmPreloadState
import player.preloadBarLabel
import player.preloadIsAffirmative
import player.preloadIsEnabled
import player.preloadLabel
import ui.tv.TvTextRow

/**
 * What [TvTitlePage] adds beside Play for a film: an outlined pill carrying
 * the same [FilmPreloadState] labels [player.preloadLabel] gives the phone,
 * a second pill for Remove once it is [FilmPreloadState.Done], and the
 * numeric progress/home-server lines as quiet text underneath — the
 * television's own reading of what the phone draws as a bar. Android-only
 * by decision: the web player has no film preload.
 *
 * Outlined as the phone's is, in the same pair: the accent line while it
 * waits to be started ([preloadIsAffirmative]), the quiet rule once it is
 * under way. A state that takes no press (Done) is drawn faint, but the
 * remote can still land on it, for [TvTextRow]'s reason; the faintness is
 * an alpha on a chain that never changes shape, so the pill the remote is
 * on keeps it when its state moves.
 */
@Composable
internal fun TvPreloadPlate(
    state: FilmPreloadState,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    queuedAheadLabel: String? = null,
    needsSpaceBudgetBytes: Long? = null,
) {
    val enabled = preloadIsEnabled(state)
    TvSpreadPill(
        text = preloadLabel(state, queuedAheadLabel, needsSpaceBudgetBytes),
        onClick = { if (enabled) onClick() },
        accent = preloadIsAffirmative(state),
        modifier =
            modifier
                .focusRequester(focusRequester)
                .alpha(if (enabled) 1f else DisabledAlpha)
                .semantics { if (!enabled) disabled() },
    )
}

/** The second pill Done offers beside the main one — see [TvPreloadPlate]. */
@Composable
internal fun TvPreloadRemovePlate(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvSpreadPill(text = "Remove preload", onClick = onClick, modifier = modifier)
}

/** How faint a pill that cannot be pressed is drawn — [TvTextRow]'s own. */
private const val DisabledAlpha = 0.5f

/** NeedsSpace's own further row — opens Settings › Storage. */
@Composable
internal fun TvPreloadStorageRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvTextRow(text = "Raise the cache budget", onClick = onClick, modifier = modifier)
}

/**
 * The same thin bar the phone draws, read from across a room rather than
 * touched — nothing here takes the remote; cancelling or resuming is
 * [TvPreloadPlate]'s own OK, not the bar's. Plain rectangles rather than
 * `TvSeekBar`'s focused/draggable look: that bar answers Left/Right for a
 * real seek, and a preload has nothing here for a direction key to do.
 */
@Composable
private fun TvPreloadBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Thickness)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
            .drawBehind {
                drawRect(color = Palette.RuleStrong, size = size)
                drawRect(color = Palette.Imprint, size = Size(size.width * fraction, size.height))
            },
    )
}

/** The bar's own thickness — same order as [ui.tv.player.TvSeekBar]'s unfocused track. */
private val Thickness = 6.dp

/**
 * Whatever quiet text [state]/[serverLine] call for, stacked under the Play
 * row's own plates — the bar and its own numeric caption
 * "2.1 of 5.8 GB · 36%" while [state] is Running or Paused (a pause does
 * not reset held/total, so the same figure keeps reading true — see
 * [ui.catalog.PreloadProgressBar]'s own doc for the phone's identical
 * reasoning), NeedsSpace's own storage link, and the paired server's own
 * line whenever there is one.
 */
@Composable
internal fun TvPreloadDetailLines(state: FilmPreloadState, serverLine: String?, onOpenStorage: () -> Unit, modifier: Modifier = Modifier) {
    val (held, total) =
        when (state) {
            is FilmPreloadState.Running -> state.heldBytes to state.totalBytes
            is FilmPreloadState.Paused -> state.heldBytes to state.totalBytes
            else -> null to null
        }
    Column(modifier = modifier) {
        if (held != null && total != null) {
            val fraction = if (total <= 0L) 0f else (held.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            TvPreloadBar(fraction)
            TvQuietLine(preloadBarLabel(held, total), modifier = Modifier.padding(top = Spacing.extraSmall))
        }
        if (state is FilmPreloadState.NeedsSpace) TvPreloadStorageRow(onClick = onOpenStorage, modifier = Modifier.padding(top = Spacing.small))
        serverLine?.let { TvQuietLine(it, modifier = Modifier.padding(top = Spacing.small)) }
    }
}

/** What [TvTitlePage] needs to draw a film's Preload control — the television twin of [ui.catalog.TitlePreloadUi]. */
data class TvTitlePreloadUi(
    val state: FilmPreloadState,
    val serverLine: String?,
    val onToggle: () -> Unit,
    val onRemove: () -> Unit,
    val onOpenStorage: () -> Unit,
    val queuedAheadLabel: String? = null,
    val needsSpaceBudgetBytes: Long? = null,
)
