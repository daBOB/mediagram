package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import model.humanSize
import playback.INTERNAL_VOLUME_ID
import system.CacheBudgetViewModel
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * The phone's "Where" block on a television: every volume the cache could
 * live on — a USB drive set up as removable storage among them — with its
 * free space, one row each and the chosen one marked. Choosing does not
 * move the live cache, only where the next start opens it.
 *
 * Reads the [CacheBudgetViewModel] that [TvCacheBudgetBlock] refreshes on
 * every visit, so it triggers no read of its own.
 */
@Composable
internal fun TvCacheVolumeBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val chosenId by viewModel.chosenVolumeId.collectAsStateWithLifecycle()
    if (volumes.isEmpty()) return
    // A chosen volume that is not present (the drive was pulled) marks what
    // is actually in use — internal, the only place a fallback lands.
    val selectedId = chosenId?.takeIf { id -> volumes.any { it.id == id } } ?: INTERNAL_VOLUME_ID
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(heading = "Where", rows = emptyList())
        for (volume in volumes) {
            val chosen = volume.id == selectedId
            TvTextRow(
                text = "${if (chosen) CHOSEN else NOT_CHOSEN}  ${volume.label} — ${humanSize(volume.freeBytes)} free",
                onClick = { viewModel.chooseVolume(volume.id) },
                modifier =
                    Modifier.semantics {
                        selected = chosen
                        role = Role.RadioButton
                    },
            )
        }
        TvQuietLine("Takes effect the next time the app starts. Titles already held will be fetched again.")
    }
}
