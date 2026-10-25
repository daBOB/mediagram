package ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import system.CacheBudgetViewModel
import system.cacheBudgetChoices
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * The cache block of Settings, the phone's on a television: what is held
 * against the allowance as a gauge ([TvBudgetGauge]), then the allowance as
 * one stepper ([TvBudgetStepper]) rather than a row per size. Choosing a
 * smaller one frees the difference at once, and the gauge says so on the
 * next read.
 *
 * The "Cache" heading is [entryFocusRequester]'s own target — it draws
 * whether the read has finished or not, so [TvStorageSection] always has a
 * stop ready the moment it is entered, the same reason [TvSystemSection]
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
            rows = emptyList(),
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
        TvBudgetGauge(held = current.heldBytes, budget = current.budgetBytes)
        TvBudgetStepper(
            budget = current.budgetBytes,
            choices = cacheBudgetChoices(current.capBytes),
            onChoose = viewModel::choose,
        )
    }
}

internal const val CHOSEN = "●"
internal const val NOT_CHOSEN = "○"
