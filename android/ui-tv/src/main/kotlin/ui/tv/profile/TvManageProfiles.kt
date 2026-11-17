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
import catalog.profile.PinPrompt
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
 * question does, and as the owner's view does ([TvManageAdds]). The PIN asked
 * — a tile's, or one a change asks — takes the screen ([prompt]). Done is
 * [ManageActions.onClose]; Back is [onBack], and on Who's managing? Done.
 * [spot] keeps the tile chosen there.
 */
@Composable
internal fun TvManageProfiles(
    state: ManageUiState,
    actions: ManageActions,
    onBack: () -> Unit = actions.onClose,
    spot: TvWhoSpot = remember { TvWhoSpot() },
    prompt: PinPrompt? = null,
    onPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
) {
    when (state) {
        ManageUiState.Closed -> Unit
        is ManageUiState.ChoosingActor ->
            prompt?.let { TvPinPrompt(it, onPin = onPin, onCancel = onCancelPin) } ?: TvWhoIsManaging(state, spot, actions.onActAs, actions.onClose)
        is ManageUiState.Managing -> {
            // First, so the add's and the pad's own Backs, composed after it, take Back while they are open.
            BackHandler(onBack = onBack)
            TvManaging(state, actions, prompt, onPin, onCancelPin)
        }
    }
}

@Composable
private fun TvManaging(
    state: ManageUiState.Managing,
    actions: ManageActions,
    prompt: PinPrompt?,
    onPin: (String) -> Unit,
    onCancelPin: () -> Unit,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    val part = state.parts().first()
    TvManageAdds(state, actions, prompt, onPin, onCancelPin) { adds, notice ->
        val first = remember { FocusRequester() }
        val add = remember { FocusRequester() }
        // Again whenever someone leaves the list — the row the remote was on may be the one that went — and back from an add on Add someone.
        LaunchedEffect(state.grownUps.size, state.kids.size) {
            (if (adds.backTo != null) add else first).requestFocus()
            adds.backTo = null
        }
        TvManagePage(managingAs(state.actor.name), notice) {
            TvManagePart(part, onOpen = { step = it }, onAdd = { adds.adding = part.form }, first = first, addRequester = add)
            TvTextRow(DONE, onClick = actions.onClose, modifier = Modifier.fillMaxWidth().padding(top = Spacing.medium))
        }
        TvManageDialog(step, state, actions) { step = it }
    }
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
