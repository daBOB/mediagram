package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import designsystem.Spacing

/**
 * Settings' cache block, as [ui.LibraryFlowBranches] hands it to
 * [SettingsScreen]: how much is held and allowed
 * ([CacheBudgetBlock]), then where it lives ([CacheVolumeBlock]) — both
 * read the same [system.CacheBudgetViewModel], so they stay in step
 * without either composable knowing about the other.
 */
@Composable
internal fun CacheSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        CacheBudgetBlock()
        CacheVolumeBlock()
    }
}
