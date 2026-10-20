package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import designsystem.Spacing
import designsystem.TvType
import playback.FilmPreloadState
import player.preloadBarLabel
import player.preloadIsEnabled
import player.preloadLabel
import ui.common.catalog.TitlePreloadUi
import ui.common.catalog.rowControlState
import ui.common.catalog.rowShareLabel
import ui.tv.TvTextRow

/**
 * An episode row's own Preload control on television — the phone's
 * `EpisodePreload`, laid out the way [TvPreloadsPage] gives a row its
 * action: a text row beside the title, which the remote reaches with
 * Right while OK on the title still plays. Its words are the film's
 * ([preloadLabel]); the row's own share sits before it, said in full to a
 * screen reader. Done adds [RemovePreload] beside it, as [TvTitlePage] does.
 *
 * The control's chain never changes shape — Done's faintness is an alpha,
 * as [TvPreloadPlate]'s is — and Remove hands the remote back to the
 * control before it goes, so the remote never falls off a row whose state
 * moves under it.
 */
@Composable
internal fun TvEpisodePreload(ui: TitlePreloadUi, totalBytes: Long, modifier: Modifier = Modifier) {
    val control = remember { FocusRequester() }
    val state = ui.rowControlState()
    val enabled = preloadIsEnabled(state)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
        ui.rowShareLabel(totalBytes)?.let { share ->
            TvQuietLine(share, modifier = Modifier.semantics { contentDescription = preloadBarLabel(ui.heldBytes ?: 0L, totalBytes) })
        }
        TvTextRow(
            text = preloadLabel(state, ui.queuedAheadLabel, ui.needsSpaceBudgetBytes),
            onClick = ui.onToggle,
            modifier = Modifier.alpha(if (enabled) 1f else DisabledAlpha).semantics { if (!enabled) disabled() },
            focusRequester = control,
            style = TvType.bodySmall,
        )
        if (ui.state == FilmPreloadState.Done) {
            TvTextRow(text = RemovePreload, onClick = { control.requestFocus(); ui.onRemove() }, style = TvType.bodySmall)
        }
    }
}
