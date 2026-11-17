package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.profile.HouseholdsOwnerViewModel
import catalog.profile.JoinHouseholdViewModel
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.PinPrompt
import catalog.profile.parts
import ui.common.profile.ManageActions

/**
 * The owner's view on television — the web's `ownerView`: every household
 * from one panel, its header opening the household's own steps
 * ([TvHouseholdsPanel]), its people and its Add someone Manage's
 * ([TvOwnerManage]). One owner's PIN, the page's, serves both. Drawn where
 * Manage is ([TvManageScreen]) once Manage manages as the owner. Back from
 * the rows is [onBack]; [onJoined] runs once Use on this device moved the box.
 */
@Composable
internal fun TvOwnerView(
    manage: ManageProfilesViewModel,
    state: ManageUiState.Managing,
    prompt: PinPrompt?,
    actions: ManageActions,
    onBack: () -> Unit,
    onJoined: () -> Unit,
) {
    val owner: HouseholdsOwnerViewModel = hiltViewModel()
    val join: JoinHouseholdViewModel = hiltViewModel()
    TvHouseholdsPanel(owner, join::reread, onDone = onBack, onJoined = onJoined) { landing, go ->
        TvOwnerManage(state, actions, landing, go, prompt, manage::enterPin, manage::cancelPin)
    }
}

/**
 * The owner's view ([TvOwnerRows]) and Manage's steps on it, over [state]. An
 * add — into a household, by its Add someone ([TvAddSomeone]) — stays on the
 * step that sent it until its answer — a PIN pad given up is none — and
 * keeps what was chosen and typed, as the web's form does: one that took
 * lands the remote on its household's header; a refusal is said on that
 * step, and goes with the add when it is given up. While one is out, presses
 * send nothing more. Manage's PIN pad ([prompt]) stands in the view's place,
 * the add held under it. [go] moves the panel around it: to a household's
 * step, or back to the rows, landing on a header.
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
) {
    val forms = rememberSaveableStateHolder()
    // The household an add is under way into.
    var adding by remember { mutableStateOf<String?>(null) }
    // The add out, and the answers counted as it went: only a later count is its answer.
    var sent by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // The add whose step says why it did not take, and that answer; none once that add was given up, said nowhere then.
    var refused by remember { mutableStateOf<Pair<String?, Int>?>(null) }
    LaunchedEffect(state.answers) {
        val (from, at) = sent ?: return@LaunchedEffect
        if (state.answers <= at) return@LaunchedEffect
        sent = null
        if (state.notice != null) {
            if (adding == from) refused = from to state.answers
            return@LaunchedEffect
        }
        forms.removeState(from)
        if (adding == from) {
            adding = null
            go(TvHouseholdStep.List(from))
        }
    }
    if (prompt != null) {
        TvPinPrompt(prompt, onPin = onPin) {
            sent = null
            onCancelPin()
        }
        return
    }
    val parts = state.parts()
    // A household gone — deleted, here or elsewhere — takes its add with it.
    val add = adding?.takeIf { one -> parts.any { it.id == one } }
    if (add == null) {
        TvOwnerRows(state, actions, landing, state.notice.takeIf { refused?.second != state.answers }, go) { adding = it }
        return
    }
    forms.SaveableStateProvider(add) {
        TvAddSomeone(
            part = parts.first { it.id == add },
            notice = state.notice.takeIf { refused == add to state.answers },
            onAdd = { name, age, dailyLimit, parentId ->
                if (sent == null) {
                    sent = add to state.answers
                    if (age != null) actions.onAddKid(name, age, dailyLimit, parentId) else actions.onAddGrownUp(name, add)
                }
            },
            onCancel = {
                forms.removeState(add)
                refused = refused?.let { null to it.second }
                adding = null
                go(TvHouseholdStep.List(add))
            },
        )
    }
}
