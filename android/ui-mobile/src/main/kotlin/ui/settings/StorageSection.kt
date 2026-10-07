package ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

/**
 * Settings › Storage: Cache (held, budget, where it lives) beside the Home
 * cache server — the approved mockup's own two columns
 * (`round2/b-storage.html`). The read itself is triggered once, by
 * `SettingsScreen` on entry; neither block below triggers its own — see
 * [CacheBudgetBlock]'s own comment on why a second trigger would read the
 * cache twice.
 *
 * Deliberately reordered from the mockup's own DOM: [CacheVolumeBlock]'s
 * "Where" row draws after the Budget picker rather than between it and
 * "Held", so each of the two cache blocks draws its own fields together
 * and neither depends on the other.
 */
@Composable
internal fun StorageSection(expanded: Boolean) {
    SettingsColumns(
        expanded = expanded,
        columns =
            listOf(
                {
                    Column {
                        CacheBudgetBlock()
                        CacheVolumeBlock()
                    }
                },
                { LanCacheBlock() },
            ),
    )
}
