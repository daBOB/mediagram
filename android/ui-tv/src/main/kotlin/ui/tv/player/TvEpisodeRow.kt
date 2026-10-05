package ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import catalog.humanDuration
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import player.EpisodeRow

/** What the open title's row says in place of its runtime. */
internal const val NOW_PLAYING = "Now playing"

/** A finished title: still there to play again, quieter than the rest. */
private const val WATCHED_ALPHA = 0.45f

private val ProgressHeight = 3.dp

/**
 * One title of the run: its number, its name and its runtime — or "Now
 * playing" for the open one, which a press leaves alone. A finished one is
 * drawn faint behind a ✓; one started and left has a line along its foot
 * for how far in, the catalogue's own progress rule.
 */
@Composable
internal fun TvEpisodeRow(
    row: EpisodeRow,
    requester: FocusRequester,
    onPick: (String) -> Unit,
) {
    TvOverlaySurface(
        onClick = { if (!row.current) onPick(row.setId) },
        enabled = true,
        modifier = Modifier.fillMaxWidth().focusRequester(requester).alpha(if (row.watched) WATCHED_ALPHA else 1f),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
                if (row.watched) Text(text = "✓", style = TvTypeScale.body)
                if (row.number.isNotEmpty()) Text(text = row.number, style = TvTypeScale.body)
                Text(text = row.title, style = TvTypeScale.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(text = if (row.current) NOW_PLAYING else humanDuration(row.runtimeSecs).orEmpty(), style = TvTypeScale.body)
            }
            row.progress?.let { done ->
                Box(modifier = Modifier.padding(top = Spacing.extraSmall).fillMaxWidth().height(ProgressHeight).background(Palette.RuleStrong)) {
                    Box(modifier = Modifier.fillMaxWidth(done).fillMaxHeight().background(LocalContentColor.current))
                }
            }
        }
    }
}
