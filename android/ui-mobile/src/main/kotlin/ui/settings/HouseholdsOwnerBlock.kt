package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdsOwnerUiState
import catalog.profile.HouseholdsOwnerViewModel
import designsystem.SectionHead
import designsystem.Spacing
import model.HouseholdWords

/**
 * Households on the Profiles page wherever the owner's view is not showing —
 * the web's `settings-households.js`: households are the owner's to set up,
 * and an Owner button. On a device that shows another household the owner is
 * not among those to choose, so this is the way in from any device: the
 * button asks the owner's PIN, and the page opens the owner's view of every
 * household with it held. An install from before the owner has no button.
 */
@Composable
internal fun HouseholdsOwnerBlock(owners: HouseholdsOwnerViewModel) {
    val state by owners.state.collectAsStateWithLifecycle()
    // The households not read yet: nothing to say about them.
    if (state is HouseholdsOwnerUiState.Loading) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(HouseholdWords.HOUSEHOLDS, style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        Text(HouseholdWords.OWNER_NOTE, style = MaterialTheme.typography.bodyLarge)
        // Open means there is an owner, whose view merely is not showing here.
        if ((state as? HouseholdsOwnerUiState.Closed)?.canOpen != false) QuietPill(HouseholdWords.OWNER, onClick = owners::open)
    }
}
