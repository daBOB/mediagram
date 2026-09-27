package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import model.humanSize
import playback.INTERNAL_VOLUME_ID
import system.CacheBudgetViewModel
import ui.components.Block
import ui.components.LedgerEntry

/**
 * Settings' "Where" row: every volume the cache could live on, with its
 * free space, and the choice a viewer makes — which does not move the live
 * cache, only what the next open uses, hence the sentence under the list.
 *
 * A single volume (internal storage, the common case) draws as one more
 * ledger row, matching the approved mockup exactly. More than one draws as
 * a row of [SettingsChip] choices instead — the mockup itself was never
 * shown with more than one, so this follows Storage's Budget row's own
 * pattern for the same question shape.
 */
@Composable
internal fun CacheVolumeBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val chosenId by viewModel.chosenVolumeId.collectAsStateWithLifecycle()
    // CacheSection triggers the read once, for every cache block sharing
    // this ViewModel; a second LaunchedEffect(Unit) here would read twice.
    if (volumes.isEmpty()) return
    // A chosen id absent from the offered rows (the card was ejected, or
    // this open fell back) selects whatever is actually in use — internal,
    // the only volume a fallback ever lands on — rather than leaving no
    // row selected at all.
    val selectedId = chosenId?.takeIf { id -> volumes.any { it.id == id } } ?: INTERNAL_VOLUME_ID
    Column(modifier = Modifier.padding(top = Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (volumes.size == 1) {
            val volume = volumes.first()
            Block(
                heading = null,
                rows =
                    listOf(
                        LedgerEntry(
                            "Where",
                            "${volume.label} — ${humanSize(volume.freeBytes)} free",
                            onClick = { viewModel.chooseVolume(volume.id) },
                            // The only choice there is, so it always reads selected.
                            selected = true,
                        ),
                    ),
            )
        } else {
            Text(text = "Where", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (volume in volumes) {
                    SettingsChip(text = volume.label, selected = volume.id == selectedId, onClick = { viewModel.chooseVolume(volume.id) })
                }
            }
        }
        Text(
            "Takes effect the next time the app starts. Titles already held will be fetched again.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
