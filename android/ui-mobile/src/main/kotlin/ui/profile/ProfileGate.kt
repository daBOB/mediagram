package ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import ui.ProfileBarState

/**
 * Gates [content] on a chosen profile: a viewer, not the setup step the app
 * already answers, decides whose shelves these are, and nothing past this
 * point knows how to draw them without one.
 *
 * Shows [ProfilePickerScreen] in place of [content] while there is nobody
 * chosen yet, and hands [content] the bar state for whoever is once there is
 * — the name [ui.LibraryScaffold] shows, and what tapping it reopens.
 *
 * Manage profiles, opened from the picker, takes the picker's place until
 * Done; once a profile is chosen it cannot be reached, so a phone left on a
 * grown-up does not hand a child the controls. It closes, and forgets its
 * PIN, when the library shows again or the app is left
 * ([ForgetManageWhenAway]).
 */
@Composable
internal fun ProfileGate(content: @Composable (ProfileBarState) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val chosen = state as? ProfileUiState.Chosen
    ForgetManageWhenAway(showsLibrary = chosen != null, picker = viewModel, manage = manage)

    when {
        chosen != null -> content(ProfileBarState(name = chosen.profile.name, onChoose = viewModel::reopen))
        managing != ManageUiState.Closed -> ManageRoute(manage, managing)
        else -> PickerRoute(viewModel, state, onManage = manage::open)
    }
}

@Composable
private fun PickerRoute(
    viewModel: ProfileViewModel,
    state: ProfileUiState,
    onManage: () -> Unit,
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
    ProfilePickerScreen(state, actions)
    pin?.let { PinDialog(it, onPin = viewModel::enterPin, onDismiss = viewModel::cancelPin) }
}

/** Manage over its view model: the screen, the PIN over it, and Back as Done — which drops the PIN it held. */
@Composable
private fun ManageRoute(
    viewModel: ManageProfilesViewModel,
    state: ManageUiState,
) {
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            ManageActions(
                viewModel::actAs,
                viewModel::addKid,
                viewModel::setKidsAge,
                viewModel::remove,
                viewModel::addGrownUp,
                viewModel::changePin,
                viewModel::close,
            )
        }
    BackHandler(onBack = viewModel::close)
    ManageProfilesScreen(state, actions)
    pin?.let { PinDialog(it, onPin = viewModel::enterPin, onDismiss = viewModel::cancelPin) }
}
