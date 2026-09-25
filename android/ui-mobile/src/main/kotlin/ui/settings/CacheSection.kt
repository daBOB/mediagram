package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import designsystem.Spacing
import system.CacheBudgetViewModel

/**
 * Settings' cache block, as [ui.LibraryFlowBranches] hands it to
 * [SettingsScreen]: how much is held and allowed
 * ([CacheBudgetBlock]), then where it lives ([CacheVolumeBlock]) — both
 * read the same [system.CacheBudgetViewModel], so they stay in step
 * without either composable knowing about the other.
 *
 * The read itself is triggered once, here — not by either block. Both
 * resolve the same Hilt-scoped instance this [hiltViewModel] call does, so
 * a `LaunchedEffect(Unit) { refresh() }` in each of them would read the
 * cache and walk `StorageManager` twice on every visit for no reason.
 */
@Composable
internal fun CacheSection() {
    val viewModel: CacheBudgetViewModel = hiltViewModel()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        CacheBudgetBlock()
        CacheVolumeBlock()
    }
}
