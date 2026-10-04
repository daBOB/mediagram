package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.CHANGE_YOUR_PIN
import catalog.profile.DONE
import catalog.profile.GROWN_UPS
import catalog.profile.KIDS_SECTION
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageProfilesViewModel
import catalog.profile.ManageUiState
import catalog.profile.WHO_ARE_YOU
import catalog.profile.YOUR_PIN
import catalog.profile.managingAs
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvTextRow
import ui.tv.player.TvSettingsHeading

/** What Manage profiles' rows do — [ManageProfilesViewModel]'s actions, or a test's record of them. */
internal class TvManageActions(
    val onActAs: (id: String) -> Unit,
    val onAddKid: (name: String, age: Int) -> Unit,
    val onSetKidsAge: (id: String, age: Int) -> Unit,
    val onRemove: (id: String) -> Unit,
    val onAddGrownUp: (name: String) -> Unit,
    val onChangePin: (id: String) -> Unit,
    val onClose: () -> Unit,
)

internal fun ManageProfilesViewModel.tvActions() =
    TvManageActions(this::actAs, this::addKid, this::setKidsAge, this::remove, this::addGrownUp, this::changePin, this::close)

/** What covers or replaces the list while it is open. */
private sealed interface TvManageStep {
    data object List : TvManageStep

    data class Adding(val kid: Boolean) : TvManageStep

    data class KidActions(val kid: Profile) : TvManageStep

    data class GrownUpActions(val grownUp: Profile) : TvManageStep
}

/**
 * Manage profiles on television — the web's panel (`profile-manage.js`) as
 * one column the remote walks: who you are; then, for the admin, the other
 * grown-ups; every grown-up's own kids ("Name · FSK N"); its own PIN; Done.
 * A row opens its few choices in a dialog rather than laying the web's
 * buttons along it, so Down always means the next person. Adding asks the
 * name on a screen of its own, in place, as every TV text question does.
 * Back is Done.
 */
@Composable
internal fun TvManageProfiles(
    state: ManageUiState,
    actions: TvManageActions,
) {
    BackHandler(onBack = actions.onClose)
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> TvWhoAreYou(state, actions)
        is ManageUiState.Managing -> TvManaging(state, actions)
    }
}

@Composable
private fun TvWhoAreYou(
    state: ManageUiState.ChoosingActor,
    actions: TvManageActions,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(state.grownUps.isEmpty()) { first.requestFocus() }
    TvManagePage(WHO_ARE_YOU, state.notice) {
        state.grownUps.forEachIndexed { index, profile ->
            key(profile.id) { TvTextRow(profile.name, onClick = { actions.onActAs(profile.id) }, focusRequester = first.takeIf { index == 0 }) }
        }
        TvTextRow(DONE, onClick = actions.onClose, focusRequester = first.takeIf { state.grownUps.isEmpty() }, modifier = Modifier.padding(top = Spacing.medium))
    }
}

@Composable
private fun TvManaging(
    state: ManageUiState.Managing,
    actions: TvManageActions,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    (step as? TvManageStep.Adding)?.let { adding ->
        TvAddProfileFlow(
            heading = if (adding.kid) ADD_A_KID else ADD_A_GROWN_UP,
            askLimit = adding.kid,
            onAdd = { name, age ->
                step = TvManageStep.List
                if (age != null) actions.onAddKid(name, age) else actions.onAddGrownUp(name)
            },
            onCancel = { step = TvManageStep.List },
        )
        return
    }
    val admin = state.canAddGrownUp
    val first = remember { FocusRequester() }
    // Again whenever someone leaves the list: the row the remote was on may be the one that went.
    LaunchedEffect(state.grownUps.size, state.kids.size) { first.requestFocus() }
    TvManagePage(managingAs(state.actor.name), state.notice) {
        if (admin) {
            TvSettingsHeading(GROWN_UPS)
            state.grownUps.forEachIndexed { index, grownUp ->
                key(grownUp.id) {
                    TvTextRow(grownUp.name, onClick = { step = TvManageStep.GrownUpActions(grownUp) }, focusRequester = first.takeIf { index == 0 })
                }
            }
            TvTextRow(ADD_A_GROWN_UP, onClick = { step = TvManageStep.Adding(kid = false) }, focusRequester = first.takeIf { state.grownUps.isEmpty() })
        }
        TvSettingsHeading(KIDS_SECTION)
        state.kids.forEachIndexed { index, kid ->
            key(kid.id) {
                TvTextRow("${kid.name} · FSK ${kid.kidsLimit}", onClick = { step = TvManageStep.KidActions(kid) }, focusRequester = first.takeIf { !admin && index == 0 })
            }
        }
        TvTextRow(ADD_A_KID, onClick = { step = TvManageStep.Adding(kid = true) }, focusRequester = first.takeIf { !admin && state.kids.isEmpty() })
        TvSettingsHeading(YOUR_PIN)
        TvTextRow(CHANGE_YOUR_PIN, onClick = { actions.onChangePin(state.actor.id) })
        TvTextRow(DONE, onClick = actions.onClose, modifier = Modifier.padding(top = Spacing.medium))
    }
    val back = { step = TvManageStep.List }
    when (val current = step) {
        is TvManageStep.KidActions -> TvKidActions(current.kid, onAge = { actions.onSetKidsAge(current.kid.id, it) }, onRemove = { actions.onRemove(current.kid.id) }, onDismiss = back)
        is TvManageStep.GrownUpActions ->
            TvGrownUpActions(
                current.grownUp,
                onResetPin = {
                    back()
                    actions.onChangePin(current.grownUp.id)
                },
                onRemove = { actions.onRemove(current.grownUp.id) },
                onDismiss = back,
            )
        else -> Unit
    }
}

/** The page both steps share: Manage's heading, who it is for, and why the last change did not take. */
@Composable
private fun TvManagePage(
    subheading: String,
    notice: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Text(MANAGE_PROFILES, style = TvTypeScale.title)
        Text(subheading, style = TvTypeScale.body, modifier = Modifier.padding(bottom = Spacing.small))
        notice?.let { Text(it, style = TvTypeScale.body, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = Spacing.small)) }
        content()
    }
}
