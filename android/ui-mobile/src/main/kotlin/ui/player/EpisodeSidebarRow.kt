package ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import catalog.humanDuration
import designsystem.Spacing
import player.EpisodeRow

/** What the open title's row says in place of its runtime. */
internal const val NOW_PLAYING = "Now playing"

/** A watched row: still readable, plainly done. */
private const val WATCHED_ALPHA = 0.45f

/**
 * One title of the run: its number, its name and how long it runs. Watched
 * is drawn faint with a ✓ and is still playable; a title started and left
 * carries the catalogue's own progress line under it; the open title says
 * "Now playing" and has nothing to press.
 */
@Composable
internal fun EpisodeSidebarRow(
    row: EpisodeRow,
    onPick: (setId: String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (row.current) Modifier else Modifier.clickable { onPick(row.setId) })
                .heightIn(min = MIN_TARGET)
                .alpha(if (row.watched) WATCHED_ALPHA else 1f)
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.number.isNotEmpty()) Text(text = row.number, style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (row.watched) "✓ ${row.title}" else row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = if (row.current) NOW_PLAYING else humanDuration(row.runtimeSecs).orEmpty(), style = MaterialTheme.typography.labelMedium)
        }
        row.progress?.let { fraction ->
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.extraSmall))
        }
    }
}
