package ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.EVERYONE_HERE
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileViewModel
import catalog.profile.SWITCH_PROFILE
import catalog.profile.WHO_IS_WATCHING
import catalog.profile.everyoneHere
import designsystem.LocalCatalogueTones
import designsystem.SectionHead
import designsystem.Spacing
import model.HouseholdWords
import model.Profile
import setup.ProfileSettingsViewModel
import setup.ProfileSubtitleChoices
import setup.profileStatus
import ui.common.profile.manageActions
import ui.profile.JoinHousehold
import ui.profile.ManageProfilesScreen
import ui.profile.PinDialog
import ui.profile.ProfileRow

/**
 * The one Profiles page — the web's (`settings-profiles.js`): You — who is
 * watching, the way to switch, their subtitle language — then everyone in the
 * household, then managing them, then, while there is more than one, the
 * household this device shows, then the Owner button. While the owner
 * manages, the owner's view of every household takes the place of everyone
 * here and the button. A kid sees only You; with nobody [chosen], which is
 * how the picker's Manage profiles opens it, there is no You. Seeing the list
 * needs no PIN; changing anything asks who you are and that grown-up's PIN —
 * the owner's only at the first change — held only for the visit
 * ([ui.common.settings.ProfilesVisit]).
 *
 * The web's System link is left out: System is a row of this same index here.
 */
@Composable
internal fun ProfilesPage(chosen: Profile?) {
    Column(verticalArrangement = Arrangement.spacedBy(40.dp)) {
        chosen?.let { You(it) }
        // A kid manages nothing, so is shown nobody else either.
        if (chosen?.kids != true) Household()
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
 * Everyone here, then Manage profiles inline, and the PIN it asks for over
 * them, then This device and Households — or, while the owner manages, the
 * owner's view with each household's changes in it ([OwnerHouseholds]) and
 * This device. The Owner button's PIN opens that view in Manage's place.
 */
@Composable
private fun Household() {
    val manage: ManageProfilesViewModel = hiltViewModel()
    val owners: HouseholdsOwnerViewModel = hiltViewModel()
    val profiles by manage.profiles.collectAsStateWithLifecycle()
    val state by manage.state.collectAsStateWithLifecycle()
    val pin by manage.pin.collectAsStateWithLifecycle()
    val ownersPin by owners.pin.collectAsStateWithLifecycle()
    val actions = remember(manage) { manage.manageActions() }
    // Here, where the button is, as nothing replays the view to a later collector. Only an open Manage
    // takes the owner's place: opening a closed one would put this page over the picker.
    LaunchedEffect(owners, manage) { owners.ownerView.collect { if (manage.state.value != ManageUiState.Closed) manage.asOwner() } }
    // The owner's view says all that Everyone here says, and is where the Owner button leads.
    val ownersView = (state as? ManageUiState.Managing)?.ownersView == true
    if (!ownersView) EveryoneHere(profiles)
    OwnerHouseholds(owners) { header, create -> ManageProfilesScreen(state, actions, header, create) }
    pin?.let { PinDialog(it, onPin = manage::enterPin, onDismiss = manage::cancelPin) }
    ThisDevice()
    if (!ownersView) HouseholdsOwnerBlock(owners)
    ownersPin?.let { PinDialog(it, onPin = owners::enterPin, onDismiss = owners::cancelPin) }
}

/** Everyone in the household this device shows, each with the line that says who they are. */
@Composable
private fun EveryoneHere(profiles: List<Profile>) {
    Column {
        Text(EVERYONE_HERE, style = SectionHead, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Spacing.small))
        for ((profile, line) in everyoneHere(profiles)) {
            ProfileRow(profile.name) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The household this device shows, and Switch household, which opens Join a
 * household in its place — only while there is more than one. The gate asks
 * who is watching again once a join leaves the chosen profile behind.
 */
@Composable
private fun ThisDevice() {
    val join: JoinHouseholdViewModel = hiltViewModel()
    val state by join.state.collectAsStateWithLifecycle()
    var switching by rememberSaveable { mutableStateOf(false) }
    // As the page opens: a household made or renamed on another device changes nobody here, so nothing
    // else would read it. The owner's view, and Households below, read the same list.
    LaunchedEffect(join) { join.reread() }
    if (!state.canSwitch) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(HouseholdWords.THIS_DEVICE, style = SectionHead, color = MaterialTheme.colorScheme.onSurface)
        Text(state.here?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        if (switching) {
            JoinHousehold(join, Modifier.padding(top = Spacing.small), titleStyle = SectionHead)
        } else {
            QuietPill(HouseholdWords.SWITCH_HOUSEHOLD, onClick = { switching = true })
        }
    }
}
