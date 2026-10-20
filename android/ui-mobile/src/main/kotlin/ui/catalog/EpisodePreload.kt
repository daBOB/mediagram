package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import playback.FilmPreloadState
import player.preloadBarLabel
import player.preloadLabel
import ui.chrome.CatalogueMenu
import ui.common.catalog.TitlePreloadUi
import ui.common.catalog.rowControlState
import ui.common.catalog.rowShareLabel
import ui.settings.QuietPill

/**
 * An episode row's own trailing Preload control — a film page's [PreloadPill]
 * and words, in the small single-line pill and capped in width, so a list
 * that can run to hundreds of rows keeps every title readable on a phone.
 *
 * Its own held share sits beside it as "42%" in every state — queued,
 * failed or waiting for space included, none of which carries a figure of
 * its own — said in full ("2.1 of 5.8 GB · 42%") to a screen reader. A row
 * held in full shows its "offline" badge instead; nothing at all at 0%.
 *
 * A film page offers "Remove preload" from its ⋯ menu once Done; a row has
 * no ⋯ of its own, so the "Preloaded ✓" pill opens that same item.
 */
@Composable
internal fun EpisodePreload(ui: TitlePreloadUi, totalBytes: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.widthIn(max = MaxWidth),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ui.rowShareLabel(totalBytes)?.let { share ->
            Text(
                text = share,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = preloadBarLabel(ui.heldBytes ?: 0L, totalBytes) },
            )
        }
        if (ui.state == FilmPreloadState.Done) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                QuietPill(text = preloadLabel(ui.state), onClick = { expanded = true }, small = true)
                CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PreloadRemoveMenuItem(onRemove = ui.onRemove, onDismiss = { expanded = false })
                }
            }
        } else {
            PreloadPill(ui.rowControlState(), ui.onToggle, queuedAheadLabel = ui.queuedAheadLabel, needsSpaceBudgetBytes = ui.needsSpaceBudgetBytes, small = true, noun = "episode")
        }
    }
}

/** What the control may take of a row: on a 360dp phone the title keeps well over a third of it. */
private val MaxWidth = 168.dp
