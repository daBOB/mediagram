package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.PinPrompt
import model.HouseholdWords
import ui.common.profile.ManageActions

/**
 * The owner's view on television — the web's `ownerView`: every household
 * from one panel, its header opening the household's own steps
 * ([TvHouseholdsPanel]), its people and its Add someone Manage's
 * ([TvOwnerManage]). One owner's PIN, the page's, serves both. Drawn where
 * Manage is ([TvManageScreen]) once Manage manages as the owner. Over the
 * picker ([overPicker]) it ends, as the web's and the tablet's page there does,
 * with This device while there is another household to switch to — Settings
 * has its own row for it. Back from the rows is [onBack]; [onJoined] runs once
 * Use on this device, or Switch household, moved the box.
 */
@Composable
internal fun TvOwnerView(
    manage: ManageProfilesViewModel,
    state: ManageUiState.Managing,
    prompt: PinPrompt?,
    actions: ManageActions,
    onBack: () -> Unit,
    onJoined: () -> Unit,
    overPicker: Boolean = false,
) {
    val owner: HouseholdsOwnerViewModel = hiltViewModel()
    val join: JoinHouseholdViewModel = hiltViewModel()
    val here by join.state.collectAsStateWithLifecycle()
    val device = here.takeIf { overPicker && it.canSwitch }?.let { listOfNotNull(HouseholdWords.THIS_DEVICE, it.here?.name).joinToString(" · ") }
    TvHouseholdsPanel(owner, join::reread, onDone = onBack, onJoined = onJoined) { landing, go ->
        TvOwnerManage(state, actions, landing, go, prompt, manage::enterPin, manage::cancelPin, device) { TvJoinHousehold(join) { onJoined() } }
    }
}

/**
 * The owner's view ([TvOwnerRows]) and Manage's steps on it, over [state]:
 * the adds ([TvManageAdds]), each landing the panel ([go]) on its household
 * once it ends, and — with [device], This device's line — Switch household
 * ([switchHousehold]) in the rows' place, Back from it landing on that line.
 */
@Composable
internal fun TvOwnerManage(
    state: ManageUiState.Managing,
    actions: ManageActions,
    landing: String?,
    go: (TvHouseholdStep) -> Unit,
    prompt: PinPrompt? = null,
    onPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
    device: String? = null,
    switchHousehold: @Composable () -> Unit = {},
) {
    var switching by remember { mutableStateOf(false) }
    var switched by remember { mutableStateOf(false) }
    TvManageAdds(state, actions, prompt, onPin, onCancelPin, onEnded = { go(TvHouseholdStep.List(it)) }) { adds, notice ->
        if (switching) {
            BackHandler {
                switching = false
                switched = true
            }
            switchHousehold()
        } else {
            val line = device?.let { TvDeviceLine(it, back = switched, onSwitch = { switching = true }, onLanded = { switched = false }) }
            TvOwnerRows(state, actions, landing, notice, go, adds, line)
        }
    }
}
