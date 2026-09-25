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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import playback.INTERNAL_VOLUME_ID
import system.CacheBudgetViewModel
import ui.components.Block
import model.humanSize

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
    // CacheSection triggers the read once, for every cache block sharing
    // this ViewModel; a second LaunchedEffect(Unit) here would read twice.
    if (volumes.isEmpty()) return
    // A chosen id absent from the offered rows (the card was ejected, or
    // this open fell back) selects whatever is actually in use — internal,
    // the only volume a fallback ever lands on — rather than leaving no
    // row selected at all.
    val selectedId = chosenId?.takeIf { id -> volumes.any { it.id == id } } ?: INTERNAL_VOLUME_ID
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
