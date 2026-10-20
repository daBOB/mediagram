package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import catalog.profile.parts
import designsystem.SectionHead
import designsystem.Spacing
import model.Profile
import ui.common.profile.ManageActions
import ui.settings.QuietPill

/**
 * Manage profiles on the phone — the web's `profile-manage.js`, section for
 * section: who you are; then, for the admin, the other grown-ups and a way to
 * add one; for every grown-up, its own kids at FSK 6 or 12 and a way to add
 * one; for the owner, every other household's people too, each household
 * under its name ([ManagePartRows]); and its own PIN. The PIN itself is
 * asked by [PinDialog] over this; a removal asks first ([RemoveProfileDialog]).
 * Why the last change did not take is said at the end, where the web says it.
 *
 * The body of the Profiles page ([ui.settings.ProfilesPage]), wherever that
 * is shown, as the web mounts the same body: no scroll or Done of its own —
 * leaving the page is what ends the visit.
 */
@Composable
internal fun ManageProfilesScreen(
    state: ManageUiState,
    actions: ManageActions,
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> Page(WHO_ARE_YOU, state.notice) { WhoAreYou(state.grownUps, actions.onActAs) }
        is ManageUiState.Managing -> Page(managingAs(state.actor.name), state.notice) { Managing(state, actions) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoAreYou(
    grownUps: List<Profile>,
    onActAs: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small), modifier = Modifier.padding(top = Spacing.medium)) {
        grownUps.forEach { profile -> QuietPill(profile.name, onClick = { onActAs(profile.id) }) }
    }
}

@Composable
private fun Managing(
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    var removing by remember { mutableStateOf<Profile?>(null) }
    state.parts().forEach { part -> key(part.id) { ManagePartRows(part, state, actions) { removing = it } } }
    Section(YOUR_PIN)
    QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
    removing?.let { target -> RemoveProfileDialog(target, onRemove = { actions.onRemove(target.id) }, onDismiss = { removing = null }) }
}

@Composable
private fun Page(
    note: String,
    notice: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(MANAGE_PROFILES, style = SectionHead)
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.small),
        )
        content()
        notice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.large).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
