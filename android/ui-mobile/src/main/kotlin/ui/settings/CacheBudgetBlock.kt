package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.CacheBudgetViewModel
import system.cacheBudgetChoices
import ui.components.Block
import ui.formatting.heldOfBudget
import ui.formatting.humanSize

/**
 * The cache block of Settings: what is held against the allowance, then the
 * allowance as a choice. Choosing a smaller one frees the difference at once,
 * and the Held row says so on the next read.
 */
@Composable
internal fun CacheBudgetBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val occupancy by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    // On every visit, not once per process: Held grows with every film played.
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val current = occupancy
        if (current == null) {
            Block(heading = "Cache", rows = emptyList())
            if (failure == null) Text("Reading the cache…", style = MaterialTheme.typography.bodySmall)
        }
        failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = viewModel::refresh) { Text("Try again") }
        }
        if (current == null) return@Column
        Block(heading = "Cache", rows = listOf("Held" to heldOfBudget(current.heldBytes, current.budgetBytes)))
        if (current.fellBack) {
            Text(
                "Could not use the chosen location; using ${current.volumeLabel} instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        for (bytes in cacheBudgetChoices(current.capBytes)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = bytes == current.budgetBytes, onClick = { viewModel.choose(bytes) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = bytes == current.budgetBytes, onClick = null)
                Text(humanSize(bytes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
