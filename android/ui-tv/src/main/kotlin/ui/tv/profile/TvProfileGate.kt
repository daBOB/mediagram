package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import catalog.profile.DONE
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.ProfileUiState
import catalog.profile.ProfileViewModel
import designsystem.Overscan
import designsystem.Spacing
import ui.common.profile.ForgetManageWhenAway
import ui.common.profile.manageActions
import ui.common.settings.SettingsSection
import ui.tv.TvTextRow
import ui.tv.settings.TvProfilesPage

/**
 * Who is watching, as the masthead needs it: the name its last entry shows,
 * and what selecting that entry does. The television twin of the phone's
 * `ui.chrome.ProfileBarState`, declared here rather than shared because that one
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
 * replaces what asked for it, the picker's Manage profiles opens Settings' own
 * Profiles page in the picker's place, with nobody chosen, and that page's
 * Manage profiles row replaces it with Manage — so nothing behind competes
 * for the remote. Manage asks who you are and that PIN on every visit, there
 * and in Settings, so entering a profile never hands a child the controls. It
 * closes, forgetting its PIN, when the library shows again or the app is left
 * (`ui.common.profile.ForgetManageWhenAway`).
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

    val chosen = state as? ProfileUiState.Chosen
    ForgetManageWhenAway(showsLibrary = chosen != null, picker = viewModel, manage = manage)
    // The picker stands in for the library, which leaves composition and with
    // it every saved place: the tab, the scroll. Held here per profile, so
    // "Stay as I am" comes back to them and another profile starts fresh.
    val places = rememberSaveableStateHolder()
    if (chosen != null) {
        places.SaveableStateProvider(chosen.profile.id) {
            content(TvChosenProfile(name = chosen.profile.name, onChoose = viewModel::reopen))
        }
        return
    }
    // Back on a reopened picker is "Stay as I am" — the phone's gate says
    // why. Registered first, so the PIN, Manage and the add flow, each with
    // a Back of its own composed after this one, take Back first while open.
    BackHandler(enabled = (state as? ProfileUiState.Picking)?.canStay == true, onBack = viewModel::stay)
    // What the remote left the picker from, and the row's scroll, kept here
    // while a PIN, the Profiles page or Manage stands in for the picker.
    var landing by remember { mutableStateOf<TvPickerSpot?>(null) }
    var profilesPage by remember { mutableStateOf(false) }
    val tiles = rememberLazyListState()
    val inManage = managing != ManageUiState.Closed
    val pickPrompt = pickPin
    when {
        inManage -> TvManageScreen(manage)
        pickPrompt != null -> TvPinPrompt(pickPrompt, onPin = viewModel::enterPin, onCancel = viewModel::cancelPin)
        profilesPage -> TvProfilesOverPicker(onManage = manage::open, onDone = { profilesPage = false })
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
                    profilesPage = true
                },
                landing = landing,
                tiles = tiles,
            )
    }
}

/**
 * The Profiles page with nobody chosen — everyone here and the Manage profiles
 * row, no You — under the page's own name, as the web's picker shows it, the
 * remote on the row the picker's own button named. Done and Back leave it for
 * the picker.
 */
@Composable
private fun TvProfilesOverPicker(
    onManage: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
    ) {
        Text(SettingsSection.PROFILES.title, style = WhoHeading, modifier = Modifier.padding(bottom = Spacing.large))
        TvProfilesPage(chosen = null, focusInContent = true, landOnManage = true, remember { FocusRequester() }, onManage)
        TvTextRow(DONE, onClick = onDone, modifier = Modifier.padding(top = Spacing.medium))
    }
}

/**
 * Manage, or the PIN it asks for in its place — one screen at a time, as
 * above — wherever it is opened: the Profiles page over the picker here, or
 * Settings' Profiles panel. [onClose] is its Done and its Back.
 */
@Composable
internal fun TvManageScreen(
    manage: ManageProfilesViewModel,
    onClose: () -> Unit = manage::close,
) {
    val state by manage.state.collectAsStateWithLifecycle()
    val prompt by manage.pin.collectAsStateWithLifecycle()
    val actions = remember(manage, onClose) { manage.manageActions(onClose) }
    prompt?.let { TvPinPrompt(it, onPin = manage::enterPin, onCancel = manage::cancelPin) } ?: TvManageProfiles(state, actions)
}
