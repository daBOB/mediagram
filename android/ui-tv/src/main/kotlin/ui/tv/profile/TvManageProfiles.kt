package ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.profile.DONE
import catalog.profile.MANAGE_PROFILES
import catalog.profile.ManageUiState
import catalog.profile.managingAs
import catalog.profile.parts
import designsystem.Overscan
import designsystem.Spacing
import model.Profile
import ui.common.profile.ManageActions
import ui.tv.TvTextRow

/** What covers or replaces Manage's rows while it is open. */
internal sealed interface TvManageStep {
    data object List : TvManageStep

    /** Add someone: Grown-up or Kid, then the add itself. */
    data object Adding : TvManageStep

    /** The manager's own row: their PIN. */
    data class You(val you: Profile) : TvManageStep

    data class KidActions(val kid: Profile) : TvManageStep

    data class DailyLimit(val kid: Profile) : TvManageStep

    /** By id: the dialog stays open across the answers, so it reads the profile afresh from each one. */
    data class Sections(val id: String) : TvManageStep

    data class GrownUpActions(val grownUp: Profile) : TvManageStep
}

/**
 * Manage profiles on television — the web's panel (`profile-manage.js`) as
 * one column the remote walks: Who's managing? ([TvWhoIsManaging]) until a
 * grown-up has said who they are with their PIN; then calm rows
 * ([TvManagePart]) — themselves, the grown-ups and kids they manage — and one
 * Add someone, then Done. The owner's view of every household is drawn by
 * [TvOwnerView] instead. A row opens its few choices in a dialog rather than
 * laying the web's controls under it, so Down always means the next person;
 * the sections it is shown are one of them, in a dialog of switches of its
 * own. Adding asks on screens of its own, in place, as every TV text
 * question does. Done is [ManageActions.onClose]; Back is [onBack], and on
 * Who's managing? Done. [spot] keeps the tile chosen there.
 */
@Composable
internal fun TvManageProfiles(
    state: ManageUiState,
    actions: ManageActions,
    onBack: () -> Unit = actions.onClose,
    spot: TvWhoSpot = remember { TvWhoSpot() },
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor -> TvWhoIsManaging(state, spot, actions.onActAs, actions.onClose)
        is ManageUiState.Managing -> {
            // First, so the add's own Backs, composed after it, take Back while it is open.
            BackHandler(onBack = onBack)
            TvManaging(state, actions)
        }
    }
}

@Composable
private fun TvManaging(
    state: ManageUiState.Managing,
    actions: ManageActions,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    val part = state.parts().first()
    if (step == TvManageStep.Adding) {
        TvAddSomeone(
            part,
            notice = null,
            onAdd = { name, age, dailyLimit, parentId ->
                step = TvManageStep.List
                if (age != null) actions.onAddKid(name, age, dailyLimit, parentId) else actions.onAddGrownUp(name, null)
            },
            onCancel = { step = TvManageStep.List },
        )
        return
    }
    val first = remember { FocusRequester() }
    // Again whenever someone leaves the list: the row the remote was on may be the one that went.
    LaunchedEffect(state.grownUps.size, state.kids.size) { first.requestFocus() }
    TvManagePage(managingAs(state.actor.name), state.notice) {
        TvManagePart(part, state, onOpen = { step = it }, onAdd = { step = TvManageStep.Adding }, first = first)
        TvTextRow(DONE, onClick = actions.onClose, modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium))
    }
    TvManageDialog(step, state, actions) { step = it }
}

/** The page both steps share: Manage's heading, who it is for, and why the last change did not take. */
@Composable
internal fun TvManagePage(
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
        Text(MANAGE_PROFILES, style = WhoHeading)
        Text(subheading, style = WhoNote, modifier = Modifier.padding(bottom = Spacing.small))
        notice?.let { Text(it, style = WhoNote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = Spacing.small)) }
        content()
    }
}
