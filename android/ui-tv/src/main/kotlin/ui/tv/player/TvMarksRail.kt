package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import designsystem.Spacing
import designsystem.TvTypeScale
import model.KidsVerdict
import player.PlayerMarksState
import player.kidsLabel

/**
 * The phone's three kept controls — Watchlist, Kids, Add to list — as a row
 * under the transport, where Down from the transport lands and Up goes back
 * to it ([up]). The web keeps these in the player because "this is where a
 * viewer is when they find out what a film actually is" (`player.js`), and
 * the wordings are the phone's and the web's own.
 *
 * The [tools] follow them on the same row, and wrap to a line of their own
 * on a stage too narrow for both — the notes column's, most often — rather
 * than run off its edge out of the remote's reach.
 *
 * The marks are absent with nothing open, as on the phone; the tools are
 * always here. The Kids mark is absent on a kids profile — a child does not
 * approve titles for itself — and dimmed but still focusable on a rated
 * title, whose rating decided and is still worth reading. [first] is
 * Watchlist, the one mark always here while there are any.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvMarksRail(
    marks: PlayerMarksState?,
    actions: TvMarksActions,
    first: FocusRequester,
    up: FocusRequester,
    modifier: Modifier = Modifier,
    tools: @Composable () -> Unit,
) {
    val toTransport = Modifier.focusProperties { this.up = up }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (marks != null) {
            MarkButton(
                label = if (marks.watchlisted) "On the list" else "Watchlist",
                onClick = actions.onToggleWatchlist,
                modifier = toTransport.focusRequester(first),
            )
            if (marks.canMarkKids) {
                MarkButton(
                    label = kidsLabel(marks),
                    onClick = actions.onToggleKids,
                    enabled = marks.kidsVerdict == KidsVerdict.UNRATED,
                    modifier = toTransport,
                )
            }
            MarkButton(label = "Add to list", onClick = actions.onAddToList, modifier = toTransport)
        }
        tools()
    }
}

/**
 * What the rail's marks do. Add to list only opens the dialog: the dialog
 * lives with the screen rather than the rail, so it outlasts the controls
 * fading behind it.
 */
internal data class TvMarksActions(
    val onToggleWatchlist: () -> Unit,
    val onToggleKids: () -> Unit,
    val onAddToList: () -> Unit,
)

@Composable
private fun MarkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TvOverlayButton(text = label, style = TvTypeScale.body, enabled = enabled, onClick = onClick, modifier = modifier, padding = Spacing.medium)
}
