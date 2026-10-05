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
import player.listLabel

/**
 * The phone's three kept controls — My List, Kids, Add to list — along the
 * top beside what is playing, where the web's slim top bar keeps them:
 * "this is where a viewer is when they find out what a film actually is"
 * (`player.js`). Down from any of them goes to the seek bar ([down]).
 *
 * Absent with nothing open, as on the phone. The Kids mark is absent on a
 * kids profile — a child does not approve titles for itself — and dimmed
 * but still focusable on a rated title, whose rating decided and is still
 * worth reading. [first] is My List, the one mark always here while there
 * are any. Wraps rather than running off a stage the notes have narrowed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvMarksRail(
    marks: PlayerMarksState?,
    actions: TvMarksActions,
    first: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (marks == null) return
    val toCard = Modifier.focusProperties { this.down = down }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MarkButton(label = listLabel(marks), onClick = actions.onToggleWatchlist, modifier = toCard.focusRequester(first))
        if (marks.canMarkKids) {
            MarkButton(label = kidsLabel(marks), onClick = actions.onKids, enabled = marks.kidsVerdict == KidsVerdict.UNRATED, modifier = toCard)
        }
        MarkButton(label = "Add to list", onClick = actions.onAddToList, modifier = toCard)
    }
}

/**
 * What the marks do. Kids and Add to list only open their dialogs: those
 * live with the screen rather than the rail, so they outlast the controls
 * fading behind it.
 */
internal data class TvMarksActions(
    val onToggleWatchlist: () -> Unit,
    val onKids: () -> Unit,
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
