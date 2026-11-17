package ui.tv.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import catalog.profile.ADD_A_GROWN_UP
import catalog.profile.ADD_A_KID
import catalog.profile.DONE
import catalog.profile.ManageUiState
import catalog.profile.everyoneHere
import catalog.profile.managingAs
import catalog.profile.parts
import designsystem.Spacing
import designsystem.TvType
import model.HouseholdWords
import ui.common.profile.ManageActions
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine

/**
 * The owner's view as rows the remote walks — the web's `ownerView`: every
 * household, this device's first, as its header ("Name · Admin X · N
 * profiles"), whose OK opens its actions; then everyone in it, grown-ups
 * first, each with the line Everyone here gives them — the owner's own row
 * asking their new PIN, the one thing on it to change; anyone else's opening
 * their choices in a dialog; one the owner may not change drawn with its line
 * alone, which the remote passes over — then Add a grown-up, and Add a kid
 * where a grown-up is ([onAdd]). Then Create a household, and Done. A
 * household the list does not name yet keeps its people, unheaded, as on the
 * web. [notice] says why the last change did not take.
 *
 * The remote lands on household [landing]'s header — on Create a household
 * for [NEW] — or the first; and there again when the person whose row it was
 * on leaves the view. Opening someone's choices makes their household the
 * landing, so it is theirs.
 */
@Composable
internal fun TvOwnerRows(
    state: ManageUiState.Managing,
    actions: ManageActions,
    landing: String?,
    notice: String?,
    go: (TvHouseholdStep) -> Unit,
    onAdd: (household: String, kid: Boolean) -> Unit,
) {
    var step by remember { mutableStateOf<TvManageStep>(TvManageStep.List) }
    val parts = state.parts()
    val headers = remember { mutableMapOf<String, FocusRequester>() }
    val create = remember { FocusRequester() }
    val headed = parts.filter { it.household != null }.mapNotNull { it.id }
    // Read once the rows are drawn, which is when their requesters are.
    fun target() = if (landing == NEW) create else (landing?.takeIf { it in headed } ?: headed.firstOrNull())?.let(headers::get) ?: create
    // The person whose row holds the remote, if one does: their going hands it to the landing, not to nothing.
    var holding by remember { mutableStateOf<String?>(null) }
    val lost = holding?.let { id -> state.everyone.none { it.id == id } } == true
    LaunchedEffect(lost) {
        holding = null
        target().requestFocus()
    }
    fun holds(id: String?) = Modifier.onFocusChanged { if (it.isFocused) holding = id }
    TvManagePage(managingAs(state.actor.name), notice) {
        parts.forEach { part ->
            val id = part.id ?: return@forEach
            key(id) {
                part.household?.let { row ->
                    TvTextRow(
                        "${row.household.name} · ${row.line}",
                        onClick = { go(TvHouseholdStep.Actions(id)) },
                        modifier = holds(null).padding(top = Spacing.medium).semantics { heading() },
                        focusRequester = remember { FocusRequester() }.also { headers[id] = it },
                        style = TvType.titleSmall,
                    )
                }
                val open = { next: TvManageStep ->
                    go(TvHouseholdStep.List(id))
                    step = next
                }
                for ((one, line) in everyoneHere(part.people)) {
                    key(one.id) {
                        val text = "${one.name} · $line"
                        when {
                            one.id in part.readOnly -> TvQuietLine(text, style = TvType.bodyMedium)
                            one.id == state.actor.id -> TvTextRow(text, onClick = { actions.onChangePin(one.id) }, holds(one.id))
                            part.kids.any { it.id == one.id } -> TvTextRow(text, onClick = { open(TvManageStep.KidActions(one)) }, holds(one.id))
                            else -> TvTextRow(text, onClick = { open(TvManageStep.GrownUpActions(one)) }, holds(one.id))
                        }
                    }
                }
                if (part.addsGrownUp) TvTextRow(ADD_A_GROWN_UP, onClick = { onAdd(id, false) }, holds(null))
                if (part.addsKid) TvTextRow(ADD_A_KID, onClick = { onAdd(id, true) }, holds(null))
            }
        }
        TvTextRow(HouseholdWords.CREATE_A_HOUSEHOLD, onClick = { go(TvHouseholdStep.CreateName()) }, holds(null).padding(top = Spacing.medium), create)
        TvTextRow(DONE, onClick = actions.onClose, holds(null).padding(top = Spacing.medium))
    }
    TvManageDialog(step, state, actions) { step = it }
}
