package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import designsystem.Spacing
import playback.FilmPreloadState
import player.heldPercentLabel
import player.preloadBarLabel
import player.preloadLabel
import ui.chrome.CatalogueMenu
import ui.common.catalog.TitlePreloadUi
import ui.settings.QuietPill

/**
 * An episode row's own trailing Preload control — a film page's [PreloadPill]
 * and words, made compact for a list that can run to hundreds of rows.
 *
 * Its held share sits beside it as "42%" while a preload runs or pauses:
 * the row's stand-in for the film page's bar, said in full ("2.1 of 5.8 GB
 * · 42%") to a screen reader. An idle episode needs no second figure — its
 * pill already reads "Preload · 42% held" — and one held in full shows the
 * row's own "offline" badge instead. Nothing at all at 0%.
 *
 * A film page offers "Remove preload" from its ⋯ menu once Done; a row has
 * no ⋯ of its own, so the "Preloaded ✓" pill opens that same item.
 */
@Composable
internal fun EpisodePreload(ui: TitlePreloadUi, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        preloadBarBytes(ui.state)?.takeIf { (held, _) -> held > 0 }?.let { (held, total) ->
            Text(
                text = heldPercentLabel(held, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = preloadBarLabel(held, total) },
            )
        }
        if (ui.state == FilmPreloadState.Done) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                QuietPill(text = preloadLabel(ui.state), onClick = { expanded = true })
                CatalogueMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PreloadRemoveMenuItem(onRemove = ui.onRemove, onDismiss = { expanded = false })
                }
            }
        } else {
            PreloadPill(ui.state, ui.onToggle, queuedAheadLabel = ui.queuedAheadLabel, needsSpaceBudgetBytes = ui.needsSpaceBudgetBytes)
        }
    }
}
