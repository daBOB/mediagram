package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
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
 * ([TvHouseholdsPanel]), its people and its adds Manage's ([TvOwnerManage]).
 * One owner's PIN, the page's, serves both. Drawn where Manage is
 * ([TvManageScreen]) once Manage manages as the owner; [state] not yet the
 * owner's, it is the Owner row's way in: the owner's PIN, and then this view
 * in Manage's place. [onJoined] runs once Use on this device moved the box.
 */
@Composable
internal fun TvOwnerView(
    manage: ManageProfilesViewModel,
    state: ManageUiState,
    prompt: PinPrompt?,
    actions: ManageActions,
    onJoined: () -> Unit,
) {
    val owner: HouseholdsOwnerViewModel = hiltViewModel()
    val join: JoinHouseholdViewModel = hiltViewModel()
    // Around the panel that asks the Owner row's PIN: the view is said once, to whoever listens then.
    LaunchedEffect(owner, manage) { owner.ownerView.collect { manage.asOwner() } }
    TvHouseholdsPanel(owner, join::reread, onDone = actions.onClose, onJoined = onJoined) { landing, go ->
        // The Owner row's PIN given, Manage not the owner's yet: a moment with nothing to draw.
        (state as? ManageUiState.Managing)?.takeIf { it.ownersView }?.let { managing ->
            TvOwnerManage(managing, actions, landing, go, prompt, manage::enterPin, manage::cancelPin)
        }
    }
}

/** An add under way in the owner's view: into household [household], a kid or a grown-up. */
private data class OwnerAdd(val household: String, val kid: Boolean) {
    /** What keeps its steps' choices while a PIN pad stands in for them. */
    val key: String get() = "$household/$kid"
}

/**
 * The owner's view ([TvOwnerRows]) and Manage's steps on it, over [state]. An
 * add stays on the step that sent it until its answer — a PIN pad given up is
 * none — and keeps what was chosen and typed, as the web's forms do: one that
 * took lands the remote on its household's header; a refusal is said on that
 * step. While one is out, presses send nothing more. Manage's PIN pad
 * ([prompt]) stands in the view's place, the add held under it. [go] moves
 * the panel around it: to a household's step, or back to the rows, landing on
 * a header.
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
    var adding by remember { mutableStateOf<OwnerAdd?>(null) }
    // The add out, and the answers counted as it went: only a later count is its answer.
    var sent by remember { mutableStateOf<Pair<OwnerAdd, Int>?>(null) }
    // The add whose step says why it did not take, and that answer.
    var refused by remember { mutableStateOf<Pair<OwnerAdd, Int>?>(null) }
    LaunchedEffect(state.answers) {
        val (from, at) = sent ?: return@LaunchedEffect
        if (state.answers <= at) return@LaunchedEffect
        sent = null
        if (state.notice != null) {
            if (adding == from) refused = from to state.answers
            return@LaunchedEffect
        }
        forms.removeState(from.key)
        if (adding == from) {
            adding = null
            go(TvHouseholdStep.List(from.household))
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
    val add = adding?.takeIf { one -> parts.any { it.id == one.household } }
    if (add == null) {
        TvOwnerRows(state, actions, landing, state.notice.takeIf { refused?.second != state.answers }, go) { household, kid -> adding = OwnerAdd(household, kid) }
        return
    }
    val part = parts.first { it.id == add.household }
    forms.SaveableStateProvider(add.key) {
        TvAddProfileFlow(
            heading = "${if (add.kid) ADD_A_KID else ADD_A_GROWN_UP} · ${part.name}",
            askLimit = add.kid,
            parents = if (add.kid) part.kidParents else emptyList(),
            parentDefault = part.kidParentDefault?.id,
            notice = state.notice.takeIf { refused == add to state.answers },
            onAdd = { name, age, dailyLimit, parentId ->
                if (sent == null) {
                    sent = add to state.answers
                    if (age != null) actions.onAddKid(name, age, dailyLimit, parentId) else actions.onAddGrownUp(name, add.household)
                }
            },
            onCancel = {
                forms.removeState(add.key)
                adding = null
                go(TvHouseholdStep.List(add.household))
            },
        )
    }
}
