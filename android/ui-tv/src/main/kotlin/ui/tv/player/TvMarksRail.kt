package ui.tv.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
 * Absent entirely with nothing open, as on the phone. The Kids mark is
 * absent on a kids profile — a child does not approve titles for itself —
 * and dimmed but still focusable on a rated title, whose rating decided and
 * is still worth reading. [first] is Watchlist, the one mark always here.
 */
@Composable
internal fun TvMarksRail(
    marks: PlayerMarksState?,
    actions: TvMarksActions,
    first: FocusRequester,
    up: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (marks == null) return
    val toTransport = Modifier.focusProperties { this.up = up }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    TvOverlayButton(text = label, style = TvTypeScale.body, enabled = enabled, onClick = onClick, modifier = modifier)
}
