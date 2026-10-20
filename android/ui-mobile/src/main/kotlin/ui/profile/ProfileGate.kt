package ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.DONE
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import designsystem.Spacing
import kotlinx.coroutines.flow.merge
import ui.chrome.ProfileBarState
import ui.common.profile.ForgetManageWhenAway
import ui.common.settings.SettingsSection
import ui.settings.ProfilesPage
import ui.settings.QuietPill

/**
 * Gates [content] on a chosen profile: a viewer, not the setup step the app
 * already answers, decides whose shelves these are, and nothing past this
 * point knows how to draw them without one.
 *
 * Shows [ProfilePickerScreen] in place of [content] while there is nobody
 * chosen yet, and hands [content] the bar state for whoever is once there is
 * — the name [ui.chrome.LibraryScaffold] shows, and what tapping it reopens.
 *
 * The picker's Manage profiles opens Settings' own Profiles page in the
 * picker's place, with nobody chosen, until Done. Manage asks who you are and
 * that PIN on every visit, there and in Settings, so a phone left on a
 * grown-up does not hand a child the controls. It closes, and forgets its
 * PIN, when the library shows again or the app is left ([ForgetManageWhenAway]).
 *
 * Before all of it, a device that has joined no household is asked which
 * ([JoinHouseholdRoute]). Joining one — here, by Switch household, or by the
 * owner's Use on this device — asks who is watching again once the profile
 * chosen here is left behind.
 */
@Composable
internal fun ProfileGate(content: @Composable (ProfileBarState) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val join: JoinHouseholdViewModel = hiltViewModel()
    val owners: HouseholdsOwnerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val joining by join.state.collectAsStateWithLifecycle()
    val joinPin by join.pin.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen
    ForgetManageWhenAway(showsLibrary = chosen != null, picker = viewModel, manage = manage)
    // Here, not on the page that switched: that page leaves with the chosen profile
    // before the join has said so, and a chosen profile no longer here reads as loading.
    LaunchedEffect(join, owners) {
        merge(join.joined, owners.joined).collect { kept ->
            if (!kept) {
                manage.close()
                viewModel.reopen()
            }
        }
    }
    // The picker stands in for the library, which leaves composition and with
    // it every saved place: the tab, the scroll. Held here per profile, so
    // "Stay as I am" comes back to them and another profile starts fresh.
    val places = rememberSaveableStateHolder()

    when {
        joining.needsJoin -> JoinHouseholdRoute(join)
        chosen != null ->
            places.SaveableStateProvider(chosen.profile.id) {
                content(ProfileBarState(name = chosen.profile.name, onChoose = viewModel::reopen))
            }
        managing != ManageUiState.Closed -> ProfilesOverPicker(onDone = manage::close)
        else -> PickerRoute(viewModel, state, onManage = manage::open, insteadOfFirstProfile = insteadOfFirstProfile(join, joining))
    }
    // Over whatever shows, for the same reason: Switch household's page is gone before its join is read again.
    joinPin?.let { PinDialog(it, onPin = join::enterPin, onDismiss = join::cancelPin) }
}

@Composable
private fun PickerRoute(
    viewModel: ProfileViewModel,
    state: ProfileUiState,
    onManage: () -> Unit,
    insteadOfFirstProfile: (@Composable () -> Unit)?,
) {
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onManage) {
            PickerActions(viewModel::pick, viewModel::claim, viewModel::createFirst, onManage, viewModel::stay, viewModel::retry)
        }
    // Back on a reopened picker is "Stay as I am", as Escape is on the
    // web: the picker stands in for the library, so without this Back
    // would leave the app instead of returning to it. A first run has
    // nobody to return to, and Back there leaves as it always did. The PIN
    // dialog is a window of its own, so its Back cancels the PIN first.
    BackHandler(enabled = (state as? ProfileUiState.Picking)?.canStay == true, onBack = viewModel::stay)
    ProfilePickerScreen(state, actions, insteadOfFirstProfile)
    pin?.let { PinDialog(it, onPin = viewModel::enterPin, onDismiss = viewModel::cancelPin) }
}

/**
 * The Profiles page with nobody chosen — everyone here and managing them, no
 * You — under the page's own name, as the web's picker shows it; Done and Back
 * leave it for the picker, dropping the PIN Manage held.
 */
@Composable
private fun ProfilesOverPicker(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.large)) {
        Text(SettingsSection.PROFILES.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.large))
        ProfilesPage(chosen = null)
        QuietPill(DONE, onClick = onDone, modifier = Modifier.padding(top = Spacing.large))
    }
}
