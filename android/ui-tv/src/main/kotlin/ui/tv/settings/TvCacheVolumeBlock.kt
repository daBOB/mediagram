package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvType
import model.humanSize
import playback.CacheVolume
import playback.INTERNAL_VOLUME_ID
import system.CacheBudgetViewModel
import ui.tv.TvFocus
import ui.tv.catalog.TvQuietLine

/**
 * The phone's "Where" block on a television: every volume the cache could
 * live on — a USB drive set up as removable storage among them — as a card
 * in a row, with the chosen one marked. OK on a card chooses it; focus alone
 * does not. Choosing does not move the live cache, only where the next
 * start opens it.
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TvInfoBlock(heading = "Where", rows = emptyList())
        TvVolumeCards(volumes = volumes, chosenId = chosenId, onChoose = viewModel::chooseVolume)
        TvQuietLine("Takes effect the next time the app starts. Titles already held will be fetched again.")
    }
}

/**
 * One card per volume, equal widths so a row of two or three reads as a set.
 * A chosen volume that is not present (the drive was pulled) marks what is
 * actually in use — internal, the only place a fallback lands.
 */
@Composable
internal fun TvVolumeCards(
    volumes: List<CacheVolume>,
    chosenId: String?,
    onChoose: (String) -> Unit,
) {
    val selectedId = chosenId?.takeIf { id -> volumes.any { it.id == id } } ?: INTERNAL_VOLUME_ID
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium), modifier = Modifier.height(IntrinsicSize.Max)) {
        for (volume in volumes) {
            TvVolumeCard(volume = volume, chosen = volume.id == selectedId, onChoose = { onChoose(volume.id) })
        }
    }
}

@Composable
private fun TvVolumeCard(
    volume: CacheVolume,
    chosen: Boolean,
    onChoose: () -> Unit,
) {
    val free = humanSize(volume.freeBytes)
    // The volume reports only free space; its size is the directory's own
    // filesystem. Unknown (0) draws no bar rather than a false full one.
    val total = volume.dir.totalSpace
    val primary = MaterialTheme.colorScheme.primary
    Card(
        onClick = onChoose,
        modifier =
            Modifier.width(CardWidth).fillMaxHeight().semantics(mergeDescendants = true) {
                contentDescription = "${volume.label}, $free free"
                role = Role.RadioButton
                selected = chosen
            },
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors =
            CardDefaults.colors(
                containerColor =
                    if (chosen) {
                        primary.copy(alpha = TvFocus.ChosenFillAlpha).compositeOver(MaterialTheme.colorScheme.surface)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(Spacing.medium), verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
            Text(text = if (chosen) "$CHOSEN ${volume.label}" else volume.label, style = TvType.titleSmall, maxLines = 1)
            Text(text = "$free free", style = TvType.bodySmall, color = LocalCatalogueTones.current.quiet)
            if (total > 0) {
                TvFillBar(fraction = 1f - volume.freeBytes.toFloat() / total, height = 6.dp, modifier = Modifier.padding(top = Spacing.small))
            }
        }
    }
}

private val CardWidth = 260.dp
