package ui.tv.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import designsystem.Spacing

/**
 * Settings › Storage, television-side: the cache — held, budget, where it
 * lives — then the home cache server, stacked in one column rather than the
 * phone's own two side by side, since a ten-foot page reads top to bottom.
 * [entryFocusRequester] lands on [TvCacheBudgetBlock]'s own heading, the
 * same "always there, loading or not" stop [TvSystemContent] uses for
 * Catalogue — [returningFrom] a home-cache-server panel is [TvLanCacheBlock]'s
 * own business, further down.
 */
@Composable
internal fun TvStorageSection(
    focusInContent: Boolean,
    returningFrom: TvSettingsPanel?,
    entryFocusRequester: FocusRequester,
    onOpenLanCache: (TvSettingsPanel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        TvCacheBudgetBlock(focusInContent = focusInContent, returningFrom = returningFrom, entryFocusRequester = entryFocusRequester)
        TvCacheVolumeBlock()
        TvLanCacheBlock(returningFrom = returningFrom, onOpen = onOpenLanCache)
    }
}
