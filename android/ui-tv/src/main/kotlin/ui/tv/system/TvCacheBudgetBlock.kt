package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import model.heldOfBudget
import model.humanSize
import system.CacheBudgetViewModel
import system.cacheBudgetChoices
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * The cache block of Settings, the phone's on a television: what is held
 * against the allowance, then the allowance as a choice, one row for each
 * size with the chosen one marked. Choosing a smaller one frees the
 * difference at once, and the Held row says so on the next read.
 */
@Composable
internal fun TvCacheBudgetBlock() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val occupancy by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    // On every visit, not once per process: Held grows with every film played.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val current = occupancy
        if (current == null) {
            TvInfoBlock(heading = "Cache", rows = emptyList())
            if (failure == null) TvQuietLine("Reading the cache…")
        }
        failure?.let {
            TvQuietLine(it)
            TvTextRow(text = "Try again", onClick = viewModel::refresh)
        }
        if (current == null) return@Column
        TvInfoBlock(heading = "Cache", rows = listOf("Held" to heldOfBudget(current.heldBytes, current.budgetBytes)))
        for (bytes in cacheBudgetChoices(current.capBytes)) {
            val chosen = bytes == current.budgetBytes
            TvTextRow(
                text = "${if (chosen) CHOSEN else NOT_CHOSEN}  ${humanSize(bytes)}",
                onClick = { viewModel.choose(bytes) },
                modifier =
                    Modifier.semantics {
                        selected = chosen
                        role = Role.RadioButton
                    },
            )
        }
    }
}

private const val CHOSEN = "●"
private const val NOT_CHOSEN = "○"
