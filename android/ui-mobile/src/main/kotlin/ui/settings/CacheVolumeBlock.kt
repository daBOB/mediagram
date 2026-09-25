package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import playback.INTERNAL_VOLUME_ID
import system.CacheBudgetViewModel
import ui.components.Block
import ui.formatting.humanSize

/**
 * Settings' "Where" row: every volume the cache could live on, with its
 * free space, and the choice a viewer makes — which does not move the live
 * cache, only what the next open uses, hence the sentence under the list.
 *
 * Shown even with a single row (internal storage, the common case): it
 * confirms where the cache lives rather than only appearing once there is
 * a second volume to pick.
 */
@Composable
internal fun CacheVolumeBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val chosenId by viewModel.chosenVolumeId.collectAsStateWithLifecycle()
    // On every visit, not once per process: a card inserted since the last
    // visit should appear without restarting the app.
    LaunchedEffect(Unit) { viewModel.refresh() }
    if (volumes.isEmpty()) return
    val selectedId = chosenId ?: INTERNAL_VOLUME_ID
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Block(heading = "Where", rows = emptyList())
        for (volume in volumes) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = volume.id == selectedId, onClick = { viewModel.chooseVolume(volume.id) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = volume.id == selectedId, onClick = null)
                Text("${volume.label} — ${humanSize(volume.freeBytes)} free", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Takes effect the next time the app starts. Titles already held will be fetched again.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
