package ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.DONE
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.NEW_KID_LIMIT
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import model.Profile
import ui.settings.QuietPill

/**
 * Manage profiles on the phone — the web's `profile-manage.js`, section for
 * section: who you are; then, for the admin, the other grown-ups and a way to
 * add one; for every grown-up, its own kids at FSK 6 or 12 and a way to add
 * one; and its own PIN. The PIN itself is asked by [PinDialog] over this; a
 * removal asks first ([RemoveProfileDialog]). Why the last change did not
 * take is said above Done, where the web says it.
 */
@Composable
internal fun ManageProfilesScreen(
    state: ManageUiState,
    actions: ManageActions,
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> Page(WHO_ARE_YOU, state.notice, actions.onClose) { WhoAreYou(state.grownUps, actions.onActAs) }
        is ManageUiState.Managing -> Page(managingAs(state.actor.name), state.notice, actions.onClose) { Managing(state, actions) }
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
    var newLimit by rememberSaveable { mutableIntStateOf(NEW_KID_LIMIT) }
    if (state.canAddGrownUp) {
        Section(GROWN_UPS)
        state.grownUps.forEach { grownUp ->
            ProfileRow(grownUp.name) {
                QuietPill(RESET_PIN, onClick = { actions.onChangePin(grownUp.id) }, small = true)
                RemovePill(grownUp) { removing = it }
            }
        }
        NameForm(ADD_A_GROWN_UP, onSubmit = actions.onAddGrownUp, modifier = Modifier.padding(top = Spacing.small))
    }
    Section(KIDS_SECTION)
    state.kids.forEach { kid ->
        ProfileRow(kid.name) {
            LimitChoice(kid.kidsLimit, "Age limit for ${kid.name}") { age -> actions.onSetKidsAge(kid.id, age) }
            RemovePill(kid) { removing = it }
        }
    }
    NameForm(
        ADD_A_KID,
        onSubmit = { name ->
            actions.onAddKid(name, newLimit)
            newLimit = NEW_KID_LIMIT
        },
        modifier = Modifier.padding(top = Spacing.small),
    ) { LimitChoice(newLimit, "Age limit for the new kid") { newLimit = it } }
    Section(YOUR_PIN)
    QuietPill(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
    removing?.let { target -> RemoveProfileDialog(target, onRemove = { actions.onRemove(target.id) }, onDismiss = { removing = null }) }
}

@Composable
private fun Page(
    note: String,
    notice: String?,
    onDone: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large)) {
        Text(MANAGE_PROFILES, style = MaterialTheme.typography.headlineSmall)
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
        QuietPill(DONE, onClick = onDone, modifier = Modifier.padding(top = Spacing.large))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.small))
}

/** A name and its controls on one line, a soft rule beneath — the web's `manage-row`. */
@Composable
private fun ProfileRow(
    name: String,
    controls: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        controls()
    }
    HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
}

/** Named for whom it removes, so a screen reader does not hear a column of "Remove". */
@Composable
private fun RemovePill(
    profile: Profile,
    onRemove: (Profile) -> Unit,
) = QuietPill(REMOVE, onClick = { onRemove(profile) }, small = true, contentDescription = "$REMOVE ${profile.name}")
