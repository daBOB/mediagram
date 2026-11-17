package ui.tv.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import catalog.profile.ManagePart
import catalog.profile.ManageUiState
import catalog.profile.PinPrompt
import catalog.profile.parts
import ui.common.profile.ManageActions

/**
 * The adds on Manage, each by its part's form ([form]): the one under way
 * ([adding]); the one out, with the answers counted as it went ([sent]) — only
 * a later count is its answer; the one whose step says why it did not take,
 * and that answer ([refused]) — no form once that add was given up, said
 * nowhere then; and the one whose Add someone the remote returns to once the
 * rows are drawn again ([backTo]), which the rows clear as they land.
 */
@Stable
internal class TvAdds {
    var adding by mutableStateOf<String?>(null)
    var sent by mutableStateOf<Pair<String, Int>?>(null)
    var refused by mutableStateOf<Pair<String?, Int>?>(null)
    var backTo by mutableStateOf<String?>(null)
}

/** A part's form: its household, "" for the one part that has none. */
internal val ManagePart.form: String get() = id.orEmpty()

/**
 * Manage's [rows] with Add someone ([TvAddSomeone]) in their place while an
 * add is under way — the owner's view and anyone else's alike, as the web's
 * one `profile-add-someone.js` serves both. An add stays on the step that sent
 * it until its answer — a PIN pad given up is none — and keeps what was
 * chosen and typed: a refusal is said on that step; one that took, or one
 * given up, leaves for the rows, the remote back on its Add someone, and
 * [onEnded] hears which form. While one is out, presses send nothing more.
 * Manage's PIN pad ([prompt]) stands in the rows' place, the add held under
 * it. [rows] hears why the last change did not take, unless an add says so.
 */
@Composable
internal fun TvManageAdds(
    state: ManageUiState.Managing,
    actions: ManageActions,
    prompt: PinPrompt?,
    onPin: (String) -> Unit,
    onCancelPin: () -> Unit,
    onEnded: (form: String) -> Unit = {},
    rows: @Composable (adds: TvAdds, notice: String?) -> Unit,
) {
    val forms = rememberSaveableStateHolder()
    val adds = remember { TvAdds() }

    fun end(form: String) {
        forms.removeState(form)
        adds.adding = null
        adds.backTo = form
        onEnded(form)
    }
    LaunchedEffect(state.answers) {
        val (from, at) = adds.sent ?: return@LaunchedEffect
        if (state.answers <= at) return@LaunchedEffect
        adds.sent = null
        when {
            state.notice != null -> if (adds.adding == from) adds.refused = from to state.answers
            adds.adding == from -> end(from)
            else -> forms.removeState(from)
        }
    }
    if (prompt != null) {
        TvPinPrompt(prompt, onPin = onPin) {
            adds.sent = null
            onCancelPin()
        }
        return
    }
    // A household gone — deleted, here or elsewhere — takes its add with it.
    val part = adds.adding?.let { form -> state.parts().find { it.form == form } }
    if (part == null) return rows(adds, state.notice.takeIf { adds.refused?.second != state.answers })
    forms.SaveableStateProvider(part.form) {
        TvAddSomeone(
            part,
            notice = state.notice.takeIf { adds.refused == part.form to state.answers },
            onAdd = { name, age, dailyLimit, parentId ->
                if (adds.sent == null) {
                    adds.sent = part.form to state.answers
                    if (age != null) actions.onAddKid(name, age, dailyLimit, parentId) else actions.onAddGrownUp(name, part.id)
                }
            },
            onCancel = {
                adds.refused = adds.refused?.let { null to it.second }
                end(part.form)
            },
        )
    }
}
