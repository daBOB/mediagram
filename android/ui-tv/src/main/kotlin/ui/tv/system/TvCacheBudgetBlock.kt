package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 *
 * The "Cache" heading is [entryFocusRequester]'s own target — it draws
 * whether the read has finished or not, so [TvStorageSection] always has a
 * stop ready the moment it is entered, the same reason [TvSystemContent]
 * lands on its own Catalogue heading rather than a row further down. The
 * read itself is triggered once, by the hub on entry — see that comment on
 * [TvSettingsScreen] for why a second trigger here would read it twice.
 */
@Composable
internal fun TvCacheBudgetBlock(
    focusInContent: Boolean,
    returningFrom: TvSettingsPanel?,
    entryFocusRequester: FocusRequester,
) {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    val occupancy by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    // Keyed on focusInContent alone, not also on returningFrom — see the
    // same note on TvTelegramSection's own effect for why keying on both
    // would steal focus back after TvLanCacheBlock has already landed it
    // on its own row, once the hub clears lastPanel a moment later.
    LaunchedEffect(focusInContent) {
        if (focusInContent && returningFrom == null) entryFocusRequester.requestFocus()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val current = occupancy
        // One call site, loading or not: entryFocusRequester lands here once
        // on entry, and since the heading node is never swapped out for a
        // second one once the read finishes, that focus survives the read
        // finishing rather than being left on a node that just got replaced.
        TvInfoBlock(
            heading = "Cache",
            rows = current?.let { listOf("Held" to heldOfBudget(it.heldBytes, it.budgetBytes)) }.orEmpty(),
            modifier = Modifier.focusRequester(entryFocusRequester),
            focusable = true,
        )
        if (current == null && failure == null) TvQuietLine("Reading the cache…")
        failure?.let {
            TvQuietLine(it)
            TvTextRow(text = "Try again", onClick = viewModel::refresh)
        }
        if (current == null) return@Column
        if (current.fellBack) TvQuietLine("Could not use the chosen location; using ${current.volumeLabel} instead.")
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

internal const val CHOSEN = "●"
internal const val NOT_CHOSEN = "○"
