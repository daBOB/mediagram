package ui

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
import system.CacheBudgetViewModel
import system.cacheBudgetChoices

/**
 * The cache block of Settings: what is held against the allowance, then the
 * allowance as a choice. Choosing a smaller one frees the difference at once,
 * and the Held row says so on the next read.
 */
@Composable
internal fun CacheBudgetBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val occupancy by viewModel.state.collectAsStateWithLifecycle()
    // On every visit, not once per process: Held grows with every film played.
    LaunchedEffect(Unit) { viewModel.refresh() }
    val current = occupancy ?: return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Block(heading = "Cache", rows = listOf("Held" to heldOfBudget(current.heldBytes, current.budgetBytes)))
        for (bytes in cacheBudgetChoices()) {
            Row(
                modifier = Modifier
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
