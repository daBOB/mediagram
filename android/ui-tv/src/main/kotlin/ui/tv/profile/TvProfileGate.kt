package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import ui.profile.ForgetManageWhenAway
import ui.profile.manageActions

/**
 * Who is watching, as the masthead needs it: the name its last entry shows,
 * and what selecting that entry does. The television twin of the phone's
 * `ui.ProfileBarState`, declared here rather than shared because that one
 * lives in `:ui-mobile`, which this module never sees.
 */
data class TvChosenProfile(
    val name: String,
    val onChoose: () -> Unit,
)

/**
 * The television counterpart to `ui.profile.ProfileGate`: the same
 * [ProfileViewModel] and [ManageProfilesViewModel] decide the same things
 * they decide on a phone, and [TvProfilePicker] stands in for [content] until
 * a viewer has been chosen. One screen at a time — whichever PIN is asked
 * replaces what asked for it, and Manage replaces the picker — so nothing
 * behind competes for the remote. Manage cannot be reached once a profile is
 * chosen: entering a profile never hands a child the controls, and Manage
 * closes, forgetting its PIN, when the library shows again or the app is
 * left (`ui.profile.ForgetManageWhenAway`).
 *
 * Once a viewer is chosen, [content] is handed it the way the phone's gate
 * hands its bar a `ProfileBarState`: the name, and [ProfileViewModel.reopen]
 * as what choosing it does.
 */
@Composable
internal fun TvProfileGate(content: @Composable (TvChosenProfile) -> Unit) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val manage: ManageProfilesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val managing by manage.state.collectAsStateWithLifecycle()
    val pickPin by viewModel.pin.collectAsStateWithLifecycle()
    val managePin by manage.pin.collectAsStateWithLifecycle()

    val chosen = state as? ProfileUiState.Chosen
    ForgetManageWhenAway(showsLibrary = chosen != null, picker = viewModel, manage = manage)
    if (chosen != null) {
        content(TvChosenProfile(name = chosen.profile.name, onChoose = viewModel::reopen))
        return
    }
    // Back on a reopened picker is "Stay as I am" — the phone's gate says
    // why. Registered first, so the PIN, Manage and the add flow, each with
    // a Back of its own composed after this one, take Back first while open.
    BackHandler(enabled = (state as? ProfileUiState.Picking)?.canStay == true, onBack = viewModel::stay)
    // What the remote left the picker from, and the row's scroll, kept here
    // while a PIN or Manage stands in for the picker.
    var landing by remember { mutableStateOf<TvPickerSpot?>(null) }
    val tiles = rememberLazyListState()
    val inManage = managing != ManageUiState.Closed
    val pickPrompt = pickPin
    val managePrompt = managePin
    when {
        inManage && managePrompt != null -> TvPinPrompt(managePrompt, onPin = manage::enterPin, onCancel = manage::cancelPin)
        inManage -> TvManageProfiles(managing, remember(manage) { manage.manageActions() })
        pickPrompt != null -> TvPinPrompt(pickPrompt, onPin = viewModel::enterPin, onCancel = viewModel::cancelPin)
        else ->
            TvProfilePicker(
                state = state,
                onChoose = { id ->
                    landing = TvPickerSpot.Tile(id)
                    viewModel.pick(id)
                },
                onStay = viewModel::stay,
                onRetry = viewModel::retry,
                onClaim = { id ->
                    landing = TvPickerSpot.Claim(id)
                    viewModel.claim(id)
                },
                onCreateFirst = viewModel::createFirst,
                onManage = {
                    landing = TvPickerSpot.Manage
                    manage.open()
                },
                landing = landing,
                tiles = tiles,
            )
    }
}
