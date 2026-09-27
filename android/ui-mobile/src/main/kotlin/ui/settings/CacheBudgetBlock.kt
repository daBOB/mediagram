package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import model.heldOfBudget
import model.humanSize
import system.CacheBudgetViewModel
import system.cacheBudgetChoices
import ui.components.Block
import ui.components.LedgerEntry

/**
 * The cache block of Settings: what is held against the allowance, as a
 * ledger row with its own thin accent meter, then the allowance as a row of
 * choices. Choosing a smaller one frees the difference at once, and the
 * Held row says so on the next read.
 */
@Composable
internal fun CacheBudgetBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val occupancy by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    // CacheSection triggers the read once, for every cache block sharing
    // this ViewModel; a second LaunchedEffect(Unit) here would read twice.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val current = occupancy
        if (current == null) {
            Block(heading = "Cache", rows = emptyList<Pair<String, String?>>())
            if (failure == null) Text("Reading the cache…", style = MaterialTheme.typography.bodySmall)
        }
        failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::refresh) { Text("Try again") }
        }
        if (current == null) return@Column
        val fraction = if (current.budgetBytes > 0) current.heldBytes.toFloat() / current.budgetBytes else 0f
        Block(
            heading = "Cache",
            rows = listOf(LedgerEntry("Held", heldOfBudget(current.heldBytes, current.budgetBytes), meterFraction = fraction)),
        )
        if (current.fellBack) {
            Text(
                "Could not use the chosen location; using ${current.volumeLabel} instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(modifier = Modifier.padding(top = Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(text = "Budget", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (bytes in cacheBudgetChoices(current.capBytes)) {
                    SettingsChip(text = humanSize(bytes), selected = bytes == current.budgetBytes, onClick = { viewModel.choose(bytes) })
                }
            }
        }
    }
}
