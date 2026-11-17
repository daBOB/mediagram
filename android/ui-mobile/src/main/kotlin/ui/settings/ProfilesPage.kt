package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileViewModel
import catalog.profile.SWITCH_PROFILE
import catalog.profile.WHO_IS_WATCHING
import designsystem.LocalCatalogueTones
import designsystem.SectionHead
import designsystem.Spacing
import model.HouseholdWords
import model.Profile
import setup.ProfileSettingsViewModel
import setup.ProfileSubtitleChoices
import setup.profileStatus
import ui.chrome.CatalogueTextButton
import ui.common.profile.manageActions
import ui.profile.JoinHousehold
import ui.profile.ManageProfilesScreen
import ui.profile.PinDialog

/**
 * The one Profiles page — the web's (`settings-profiles.js`): You — who is
 * watching, the way to switch, their subtitle language — then managing the
 * household, which asks Who's managing? with the picker's tiles; watching as
 * the owner, the owner's view of every household opens at once instead. Then,
 * while there is more than one household, the one this device shows. A kid
 * sees only You; with nobody [chosen], which is how the picker's Manage
 * profiles opens it, there is no You, and This device only with the owner's
 * view. Changing anything asks that grown-up's PIN — the owner's only at the
 * first change — held only for the visit ([ui.common.settings.ProfilesVisit]).
 *
 * The web's System link is left out: System is a row of this same index here.
 */
@Composable
internal fun ProfilesPage(chosen: Profile?) {
    Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        chosen?.let { You(it) }
        // A kid manages nothing, so is shown nobody else either.
        if (chosen?.kids != true) Household(overPicker = chosen == null)
    }
}

/** Who is watching and the way to switch, then their default subtitle language. */
@Composable
private fun You(profile: Profile) {
    val settings: ProfileSettingsViewModel = hiltViewModel()
    val subtitle by settings.subtitle.collectAsStateWithLifecycle()
    // The gate's own picker and Manage: the activity holds one of each, so this reopens what the bar's name does.
    val picker: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(text = WHO_IS_WATCHING, style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        Text(text = profileStatus(profile), style = MaterialTheme.typography.bodyLarge)
        // Manage closed first: the gate shows it in the picker's place while it is open.
        QuietPill(
            SWITCH_PROFILE,
            onClick = {
                manage.close()
                picker.reopen()
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(text = "Subtitles", style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            for ((value, label) in ProfileSubtitleChoices) {
                SettingsChip(text = label, selected = value == subtitle, onClick = { settings.chooseSubtitle(value) })
            }
        }
        Text(
            text = "Forced subtitles still appear when a film switches language.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalCatalogueTones.current.quiet,
        )
    }
}

/**
 * Manage profiles inline, the owner's household changes in its owner's view
 * ([OwnerHouseholds]), and the PINs either asks over them; then This device.
 * Over the picker ([overPicker]) Who's managing? stands alone, set as the
 * picker's own question, until the owner's view opens.
 */
@Composable
private fun Household(overPicker: Boolean) {
    val manage: ManageProfilesViewModel = hiltViewModel()
    val owners: HouseholdsOwnerViewModel = hiltViewModel()
    val state by manage.state.collectAsStateWithLifecycle()
    val pin by manage.pin.collectAsStateWithLifecycle()
    val ownersPin by owners.pin.collectAsStateWithLifecycle()
    val actions = remember(manage) { manage.manageActions() }
    val headStyle = if (overPicker) MaterialTheme.typography.headlineSmall else SectionHead
    OwnerHouseholds(owners) { header, create -> ManageProfilesScreen(state, actions, header, create, headStyle) }
    pin?.let { PinDialog(it, onPin = manage::enterPin, onDismiss = manage::cancelPin) }
    if (!overPicker || (state as? ManageUiState.Managing)?.ownersView == true) ThisDevice()
    ownersPin?.let { PinDialog(it, onPin = owners::enterPin, onDismiss = owners::cancelPin) }
}

/**
 * The household this device shows, one quiet line at the page's end — the
 * web's `drawThisDevice` — with Switch household, which opens Join a
 * household under it; only while there is more than one. The gate asks who
 * is watching again once a join leaves the chosen profile behind.
 */
@Composable
private fun ThisDevice() {
    val join: JoinHouseholdViewModel = hiltViewModel()
    val state by join.state.collectAsStateWithLifecycle()
    var switching by rememberSaveable { mutableStateOf(false) }
    // As the page opens: a household made or renamed on another device changes nobody here, so nothing
    // else would read it. The owner's view reads the same list.
    LaunchedEffect(join) { join.reread() }
    if (!state.canSwitch) return
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = LocalCatalogueTones.current.ruleSoft)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.small), verticalAlignment = Alignment.CenterVertically) {
            val quiet = LocalCatalogueTones.current.quiet
            Text(HouseholdWords.THIS_DEVICE, style = MaterialTheme.typography.bodyMedium, color = quiet)
            Text(" · ", style = MaterialTheme.typography.bodyMedium, color = quiet)
            Text(state.here?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!switching) CatalogueTextButton(onClick = { switching = true }) { Text(HouseholdWords.SWITCH_HOUSEHOLD, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (switching) JoinHousehold(join, Modifier.padding(top = Spacing.small), titleStyle = SectionHead)
    }
}
